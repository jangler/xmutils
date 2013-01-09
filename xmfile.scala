import math._
import java.io.FileInputStream

object misc {
	val dutyRE = """.*@(\d).*""".r
	val pulseWidthRE = """.*pw(\d\d?).*""".r

	// Sums a list of Chars into an Int.
	def sumBytes(a: List[Char]): Int =
		(for (i <- 0 to a.length - 1) yield a(i) * pow(256, i)).sum.toInt
}

class Point(val x: Int, val y: Int)

import misc._

class Note(val xmfile: XmFile) {
	var note: Char = 0
	var instrument: Char = 0
	var volume: Char = 0
	var effectType: Char = 0
	var effectParameter: Char = 0

	var firstByte: Char = xmfile.readNext(1)(0)

	if ((firstByte & 1 << 7) != 0) {
		if ((firstByte & 1) != 0)
			note = xmfile.readNext(1)(0)
		if ((firstByte & 1 << 1) != 0)
			instrument = xmfile.readNext(1)(0)
		if ((firstByte & 1 << 2) != 0)
			volume = xmfile.readNext(1)(0)
		if ((firstByte & 1 << 3) != 0)
			effectType = xmfile.readNext(1)(0)
		if ((firstByte & 1 << 4) != 0)
			effectParameter = xmfile.readNext(1)(0)
	} else {
		note = firstByte
		instrument = xmfile.readNext(1)(0)
		volume = xmfile.readNext(1)(0)
		effectType = xmfile.readNext(1)(0)
		effectParameter = xmfile.readNext(1)(0)
	}
}

class Pattern(val xmfile: XmFile) {
	// Read header
	var headerLength: Int = sumBytes(xmfile.readNext(4))
	var packingType: Char = xmfile.readNext(1)(0)
	var numRows: Int = sumBytes(xmfile.readNext(2))
	var dataSize: Int = sumBytes(xmfile.readNext(2)) // can't be trusted

	// Parse pattern data (can't just read dataSize bytes)
	var notes = Array.ofDim[Note](numRows, xmfile.numChannels)
	for (row <- 0 to (numRows - 1)) {
		for (channel <- 0 to (xmfile.numChannels - 1)) {
			notes(row)(channel) = new Note(xmfile)
		}
	}
}

class Sample(val xmfile: XmFile) {
	// Read header
	val bytes = xmfile.readNext(4)
	var length: Int = sumBytes(bytes)
	var loopStart: Int = sumBytes(xmfile.readNext(4))
	var loopLength: Int = sumBytes(xmfile.readNext(4))

	var volume: Char = xmfile.readNext(1)(0)
	var finetune: Char = xmfile.readNext(1)(0)
	var sampleType: Char = xmfile.readNext(1)(0) // TODO: convert to structured
	var panning: Char = xmfile.readNext(1)(0)
	var relativeNoteNumber: Char = xmfile.readNext(1)(0)
	var knownValues: Char = xmfile.readNext(1)(0)
	var name: String = new String(xmfile.readNext(22) filter { _ != 0 } toArray)

	var data: List[Byte] = List[Byte]()

	def readData(): Unit = {
		data = xmfile.readNextSigned(length)
	}
}

class Instrument(val xmfile: XmFile) {
	// Read header
	var size = sumBytes(xmfile.readNext(4))
	var name = new String(xmfile.readNext(22) filter { _ != 0 } toArray)
	var duty = 0
	name match {
		case dutyRE(value) => duty = value.toInt
		case _ => ()
	}
	var pulseWidth: Int = 0
	name match {
		case pulseWidthRE(value) => pulseWidth = value.toInt
		case _ => ()
	}
	var insType = xmfile.readNext(1)(0)
	var numSamples = sumBytes(xmfile.readNext(2))

	// Assuming numSamples > 0...
	var sampleHeaderSize = sumBytes(xmfile.readNext(4))
	var sampleKeymapAssignments = xmfile.readNext(96)

	val volumePoints = for (i <- 1 to 12)
		yield new Point(sumBytes(xmfile.readNext(2)), sumBytes(xmfile.readNext(2)))
	val panningPoints = for (i <- 1 to 12)
		yield new Point(sumBytes(xmfile.readNext(2)), sumBytes(xmfile.readNext(2)))

	var numVolumePoints = xmfile.readNext(1)(0)
	var numPanningPoints = xmfile.readNext(1)(0)
	var volumeSustain = xmfile.readNext(1)(0)
	var volumeLoopStart = xmfile.readNext(1)(0)
	var volumeLoopEnd = xmfile.readNext(1)(0)
	var panningSustain = xmfile.readNext(1)(0)
	var panningLoopStart = xmfile.readNext(1)(0)
	var panningLoopEnd = xmfile.readNext(1)(0)
	var volumeType = xmfile.readNext(1)(0) // TODO: convert to structured
	var panningType = xmfile.readNext(1)(0) // TODO: convert to structured
	var vibratoType = xmfile.readNext(1)(0)
	var vibratoSweep = xmfile.readNext(1)(0)
	var vibratoDepth = xmfile.readNext(1)(0)
	var vibratoRate = xmfile.readNext(1)(0)
	var volumeFadeout = sumBytes(xmfile.readNext(2))

	// Skip reserved stuff...
	xmfile.readNext(22)

	// Read samples
	val samples = for (i <- 1 to numSamples) yield new Sample(xmfile)
	samples foreach { _ readData }
}

class XmFile(val file: FileInputStream) {

	// Reads the next n bytes from the source file.
	def readNext(n: Int): List[Char] = {
		val bytes = new Array[Byte](n)
		file.read(bytes)
		bytes.toList map { (x) => if (x < 0) (x + 256).toChar else x.toChar }
	}

	// Reads the next n signed bytes from the source file.
	def readNextSigned(n: Int): List[Byte] = {
		val bytes = new Array[Byte](n)
		file.read(bytes)
		bytes.toList
	}

	// Read XM header
	var idText: String = new String(readNext(17) toArray)
	var moduleName: String = new String(readNext(20) filter { _ != 0 } toArray)
	var escapeByte: Char = readNext(1)(0)
	var trackerName: String = new String(readNext(20) filter { _ != ' ' } toArray)
	var versionNumber: List[Char] = readNext(2)
	var headerSize: Int = sumBytes(readNext(4))
	var songLength: Int = sumBytes(readNext(2))
	var restartPosition: Int = sumBytes(readNext(2))
	var numChannels: Int = sumBytes(readNext(2))
	var numPatterns: Int = sumBytes(readNext(2))
	var numInstruments: Int = sumBytes(readNext(2))
	var flags: List[Char] = readNext(2)
	var defaultTempo: Int = sumBytes(readNext(2))
	var defaultBpm: Int = sumBytes(readNext(2))
	var orderTable: List[Char] = readNext(songLength)

	// Skip remainder of pattern order table
	readNext(256 - songLength)

	// Read patterns
	val patterns = for (i <- 1 to numPatterns) yield new Pattern(this)
	val instruments = for (i <- 1 to numInstruments) yield new Instrument(this)
}
