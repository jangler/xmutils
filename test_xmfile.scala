import java.io.FileInputStream

object TestXmFile {
	
	def test(name: String, actual: Any, expected: Any): Unit = {
		if (actual != expected)
			println("Failure: " + name + " = " + actual + "; expected " + expected)
	}

	def main(args: Array[String]) = {
		val f = new FileInputStream("do_something.xm")
		val xm = new XmFile(f)

		// XM header

		test("idText", xm.idText, "Extended Module: ")
		test("moduleName", xm.moduleName, "do a thing to stuff") 
		test("escapeByte", xm.escapeByte, 0x1A)
		test("trackerName", xm.trackerName, "MilkyTracker")
		test("versionNumber", xm.versionNumber, List(4, 1))
		test("headerSize", xm.headerSize, 276)
		test("songLength", xm.songLength, 1)
		test("restartPosition", xm.restartPosition, 0)
		test("numChannels", xm.numChannels, 8)
		test("numPatterns", xm.numPatterns, 1)
		test("numInstruments", xm.numInstruments, 5)
		test("flags", xm.flags, List(1, 0))
		test("defaultTempo", xm.defaultTempo, 5)
		test("defaultBpm", xm.defaultBpm, 135)
		test("orderTable", xm.orderTable, List(0))

		// Pattern headers

		test("patterns.length", xm.patterns.length, 1)
		for (i <- 0 to xm.patterns.length - 1) {
			test("patterns(" + i + ").headerLength", xm.patterns(i).headerLength, 9)
			test("patterns(" + i + ").packingType", xm.patterns(i).packingType, 0)
		}
		test("patterns(0).numRows", xm.patterns(0).numRows, 64)
		test("patterns(0).dataSize", xm.patterns(0).dataSize, 906)

		// Instrument header

		val insNames = List("bass", "melody", "chord", "noise", "sine")
		for (i <- 0 to insNames.length - 1)
			test("instruments(" + i + ").name", xm.instruments(i).name, insNames(i))

		// Sample header

		val sampleNames = List("2a03-triangle-high.wav", "fantasy_high.wav", 
 		                    "chord sample", "noise sample", "sine sample")
		for (i <- 0 to sampleNames.length - 1)
			test("instruments(" + i + ").samples(0).name", xm.instruments(i).samples(0).name, sampleNames(i))
	}
}
