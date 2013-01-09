import math._
import java.io.FileInputStream

// TODO: handle invalid octaves
class Format(val names: List[String], val numChannels: Int, val waitChar: Char)

object Xm2Mml {
	val generic = new Format(List(), 32, 'w')
	val nes = new Format(List("nes", "nsf", "nintendo"), 5, 'w')
	val sms = new Format(List("sms"), 4, 's')
	val gb = new Format(List("gb", "gbs", "gbc", "gameboy"), 4, 's')
	val c64 = new Format(List("c64", "commodore"), 3, 's')

	val pitchNames = List("c", "c+", "d", "d+", "e", "f", "f+", "g", "g+", "a",
	                      "a+", "b")

	var format: Format = generic
	var xm: XmFile = null

	// Fatal error
	def error(msg: String) = {
		Console.err.println("Error: " + msg)
		sys.exit
	}

	// Non-fatal error
	def warning(msg: String) = {
		Console.err.println("Warning: " + msg)
	}

  // Sets the format being used based on a String s and returns true, or
	// returns false if s does not match any format.
	def setFormat(s: String): Boolean = {
		if (nes.names contains s)
			format = nes
		else if (sms.names contains s)
			format = sms
		else if (gb.names contains s)
			format = gb
		else if (c64.names contains s)
			format = c64
		else
			return false

		true
	}

	// Reads a file into an XmFile.
	def openFile(filename: String): Unit = {
		var f: java.io.FileInputStream = null
		try {
			f = new FileInputStream(filename)
		} catch {
			case e => error("error opening file " + filename)
		}
		try {
			xm = new XmFile(f)
		} catch {
			case e => error("error reading file " + filename)
		}
	}
	
	// Print the value of a note based on how many rows pass before the next
	// note.
	def printNoteValue(n: Int): Unit = {
		var mainval = 16
		while (mainval >= 2 && mainval * n >= 32)
			mainval /= 2
		print(mainval)

		var remainder = n - (16 / mainval)
		var dotval = (16 / mainval) / 2
		while (remainder >= dotval && dotval > 0) {
			print(".")
			remainder -= dotval
			dotval /= 2
		}

		if (remainder > 0) {
			print(" " + format.waitChar)
			printNoteValue(remainder)
		}
	}

	def printPanningMacros(): Unit = {
		if (format == gb) {
			println("@CS0 = { -1 }")
			println("@CS1 = { 0 }")
			println("@CS2 = { 1 }")
		}
	}

	// TODO: make this work with 16-bit samples
	def printWaveform(insNum: Int, instrument: Instrument): Unit = {
		// Check for errors
		if (instrument.samples.length == 0)
			error("instrument " + insNum + " requires a sample for waveform " +
				"generation.")
		else if (instrument.samples.length > 1)
			warning("instrument " + insNum + " has more than one sample; using " + 
				"first sample only for waveform generation.")
		val sample = instrument.samples(0)
		if (sample.data.length < 32)
			error("sample of instrument " + insNum + " requires 32 bytes of data " +
				"for waveform generation.")
		else if (sample.data.length > 32)
			warning("sample of instrument " + insNum + " has more than 32 bytes " +
				"of data; using first 32 bytes only for waveform generation.")

		// Print waveform definition
		print("@WT" + insNum + " = { ")
		var old: Byte = 0
		for (i <- 0 to 31) {
			old = (old + sample.data(i)).toByte
			if (format == gb)
				print((old + 128) / 16 + " ")
		}
		println("}")
	}

	def printWaveformMacros(): Unit = {
		if (format == gb && xm.numChannels >= 3) {
			// Get a set of instrument numbers used in channel C
			var instruments: Set[Int] = Set()
			for (pattern <- xm.patterns) {
				val channel = 2
				for (row <- 0 to (pattern.numRows - 1)) {
					val instrument = pattern.notes(row)(channel).instrument
					if (instrument != 0)
						instruments += instrument
				}
			}
	
			// Print a waveform definition for each instrument
			for (insNo <- instruments) {
				printWaveform(insNo, xm.instruments(insNo - 1))
			}
		}
	}

	def printAdsr(insNo: Int, instrument: Instrument): Unit = {
		if (instrument.numVolumePoints != 4) {
			warning("instrument " + insNo + " has an invalid volume envelope; " +
				"using default ADSR envelope")
			println("@ADSR" + insNo + " = { 15 0 15 0 }")
		} else {
			// sloppy, sloppy approximations of SID ADSR from instrument envelope
			var a = 0
			while (pow(1.5, a) * 32 / xm.defaultBpm <= instrument.volumePoints(1).x)
				a += 1
			a = 15 - min(15, a)
			var d = 0
			while (pow(1.5, d) * 32 / xm.defaultBpm <= instrument.volumePoints(2).x - instrument.volumePoints(1).x)
				d += 1
			d = 15 - min(15, d)
			val s = min(63, instrument.volumePoints(2).y) / 4
			var r = 0
			while (pow(1.5, r) * 32 / xm.defaultBpm <= instrument.volumePoints(3).x - instrument.volumePoints(2).x)
				r += 1
			r = 15 - min(15, r)
			println("@ADSR" + insNo + " = { " + a + " " + d + " " + s + " " + r + " }")
		}
	}

	// Print an ASDR defintion for each instrument
	def printAdsrMacros(): Unit = {
		if (format == c64) {
			for (i <- 0 to (xm.numInstruments - 1)) {
				printAdsr(i + 1, xm.instruments(i))
			}
		}
	}
	
	def main(args: Array[String]) = {
		if (args.length == 2) {
			if (!setFormat(args(0)))
				error("unknown format: " + args(0))
			openFile(args(1))
		} else if (args.length == 1) {
			openFile(args(0))
		} else {
			error("syntax: xm2mml <format> <infile.xm>")
		}

		// Setup

		val numChannels = min(xm.numChannels, format.numChannels)
		if (xm.numChannels > format.numChannels)
			warning("XM file has extra channels; using first " +
				format.numChannels + " channels only.")

		// Head
		
		println("#TITLE " + xm.moduleName)

		for (letter <- 'A' to ('A' + numChannels - 1).toChar)
			print(letter)
		println(" t" + 6 * xm.defaultBpm / xm.defaultTempo)

		if (format == gb) {
			printPanningMacros
			printWaveformMacros
		} else if (format == c64) {
			printAdsrMacros
		}

		// Body
		// TODO: prevent some settings from resetting at the beginning of a pattern?

		for (patternNo <- xm.orderTable) {
			val pattern = xm.patterns(patternNo)
			var channelLetter = 'A'
			for (channel <- 0 to min(numChannels - 1, xm.numChannels - 1)) {
				print(channelLetter)
				var noteValue = 0
				var curOctave = -1
				var curVolume = -1
				var curInstrument = -1
				var curDuty = -1
				var curPulseWidth = -1
				var curNote = -1
				var curPan = 0
				var curAdsr = -1
				if (format == gb)
					print(" CS1")
				for (row <- 0 to (pattern.numRows - 1)) {
					var note = pattern.notes(row)(channel).note.toInt
					val volume = pattern.notes(row)(channel).volume.toInt
					var instrument = pattern.notes(row)(channel).instrument.toInt
					var effectType = pattern.notes(row)(channel).effectType.toInt
					var effectParameter = pattern.notes(row)(channel).effectParameter.toInt

					val pan = 
						if (effectType == 8) {
							if (effectParameter < 128) -1
							else if (effectParameter == 128) 0
							else 1
						} else {
							0
						}
					if (note == 0 && format == gb && pan != curPan) {
						note = curNote
						instrument = curInstrument
					}	

					var newVolume = curVolume
					if (format != c64 && volume >= 0x10 && volume <= 0x50) {
						if (channel == 2 && format == gb)
							newVolume = (volume - 0x10) / 17
						else
							newVolume = (volume - 0x11) / 4
						if (newVolume < 0)
							newVolume = 0
						if (note == 0) {
							note = curNote
							instrument = curInstrument
						}
					} else if (note != 0) {
						if (channel == 2 && format == gb)
							newVolume = 3
						else
							newVolume = 15
					}
					noteValue += 1

					if (note == 97) {
						if (row != 0)
							printNoteValue(noteValue)
						noteValue = 0
						print(" r")
					} else if (note != 0) {
						curNote = note
						if (row != 0)
							printNoteValue(noteValue)
						noteValue = 0

						if (format == gb) {
							if (pan != curPan) {
								curPan = pan
								if (pan < 0)
									print(" CS0")
								else if (pan == 0)
									print(" CS1")
								else
									print(" CS2")
							}
						}

						if (channel == 2 && format == gb && instrument != 0 &&
						    instrument != curInstrument) {
							curInstrument = instrument
							print(" WT" + (instrument))
						} else {
							curInstrument = instrument
						}
						if (format == c64 && curPulseWidth !=
						    xm.instruments(instrument - 1).pulseWidth) {
							curPulseWidth = xm.instruments(instrument - 1).pulseWidth
							print(" pw" + curPulseWidth)
						}
						if (format == c64 && curAdsr != instrument) {
							curAdsr = instrument
							print(" ADSR" + curAdsr)
						}
						if (xm.instruments(instrument - 1).duty != curDuty) {
							if ((format == gb && channel != 2) || (format == nes &&
									 channel != 5) || (format == c64) ||
									 (format == sms && channel == 3)) {
								curDuty = xm.instruments(instrument - 1).duty
								print(" @" + curDuty)
							}
						}

						var octave = ((note - 1) / 12)
						if (channel == 3 && format == gb)
							octave += 4
						if (curOctave != octave) {
							curOctave = octave
							print(" o" + octave)
						}
						if (newVolume != curVolume) {
							print(" v" + newVolume)
							curVolume = newVolume
						}
						print(" " + pitchNames((note - 1) % 12))
					} else {
						if (newVolume != curVolume) {
							if (row != 0)
								printNoteValue(noteValue)
							noteValue = 0
							print(" v" + newVolume)
							curVolume = newVolume
							print(" " + format.waitChar)
						} else if (row == 0) {
							print(" " + format.waitChar)
							noteValue -= 1
						}
					}
				}
				if (noteValue != pattern.numRows)
					printNoteValue(noteValue + 1)
				else
					printNoteValue(noteValue)
				println("")
				channelLetter = (channelLetter + 1).toChar
			}
		}
	}
}

