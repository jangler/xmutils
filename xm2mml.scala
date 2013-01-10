import math._
import java.io.FileInputStream
import java.util.{Map, HashMap}

// TODO: handle invalid octaves
class Format(val names: List[String], val numChannels: Int, val waitChar: Char,
	val maxVolume: List[Int]) {

	assert(maxVolume.length == numChannels)
}

object Xm2Mml {
	val generic = new Format(List(), 32, 'w', List(64,64,64,64,64,64,64,64,64,64,
		64,64,64,64,64,64,64,64,64,64,64,64,64,64,64,64,64,64,64,64,64,64))

	val nes = new Format(List("nes", "nsf", "nintendo", "2a03"), 5, 'w', 
		List(15, 15, 1, 15, 1))

	val sms = new Format(List("sms"), 4, 's', List(15, 15, 15, 15))

	val pokey = new Format(List("atari", "pokey", "at8"), 4, 's', List(15, 15, 15, 15))

	val gb = new Format(List("gb", "gbs", "gbc", "gameboy"), 4, 's',
		List(15, 15, 3, 15))

	val c64 = new Format(List("c64", "sid", "commodore"), 3, 's',
		List(15, 15, 15))

	val cpc = new Format(List("amstrad", "cpc", "ay"), 3, 's', List(15, 15, 15))

	val pitchNames = List("c", "c+", "d", "d+", "e", "f", "f+", "g", "g+", "a",
	                      "a+", "b")

	var format: Format = generic
	var xm: XmFile = null
	var lastNote: String = ""
	var arps: Map[Int, Int] = new HashMap
	var vibratos: Map[Int, Int] = new HashMap

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
		else if (cpc.names contains s)
			format = cpc
		else if (pokey.names contains s)
			format = pokey
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
		if (lastNote != "") {
			print(lastNote)
	
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
				print("&")
				printNoteValue(remainder)
			}
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
	
	// Print an @v<num> = {} volume envelope based on an instrument's volume envelope
	def printVolumeEnvelope(insNo: Int, instrument: Instrument): Unit = {
		if (instrument.numVolumePoints == 0 || !instrument.volumeOn) { // default
			println("@v" + insNo + " = { " + format.maxVolume(0) + " }")
		} else {
			val volPts = instrument.volumePoints
			val numPts = instrument.numVolumePoints
			print("@v" + insNo + " = { " +
				min(format.maxVolume(0), volPts(0).y / (64 / format.maxVolume(0))))

			val numVolumes = volPts(numPts - 1).x
			for (x <- 1 to (numVolumes - 1)) {
				var y = -1.0
				var prevIndex = 0
				for (j <- 0 to (numPts - 1)) {
					val point = volPts(j)
					if (point.x == x)
						y = point.y
					else if (point.x < x)
						prevIndex = j
				}
				if (y == -1.0) {
					val prev = volPts(prevIndex)
					var next = volPts(prevIndex + 1)
					if (prev.x < next.x) {
						val dist = 1.0 * (x - prev.x) / (next.x - prev.x)
						y = prev.y * (1 - dist) + next.y * dist
					} else {
						y = prev.y
					}
				}
				print(" " + min(format.maxVolume(0), y.toInt / (64 / format.maxVolume(0))))
			}
			print(" " +
				min(format.maxVolume(0),
				volPts(numPts - 1).y / (64 / format.maxVolume(0))))
			
			println(" }")
		}
	}

	def printVolumeEnvelopes(): Unit = {
		if (format != c64) {
			for (i <- 0 to (xm.numInstruments - 1)) {
				printVolumeEnvelope(i + 1, xm.instruments(i))
			}
		}
	}

	def printArpMacros(): Unit = {
		// Get a set of all arps that occur in the song
		var arpSet: Set[(Int, Int)] = Set()
		for (pattern <- xm.patterns) {
			for (row <- 0 to (pattern.numRows - 1)) {
				for (channel <- 0 to (xm.numChannels - 1)) {
					val effect = pattern.notes(row)(channel).effectType.toInt
					if (effect == 0) {
						val parameter = pattern.notes(row)(channel).effectParameter.toInt
						if (parameter != 0)
							arpSet += Pair((parameter & 0xf0) >> 4, parameter & 0xf)
					}
				}
			}
		}

		// Print a macro for each arp
		var index = 0
		for (arp <- arpSet) {
			println("@EN" + index + " = { 0 | " + arp._1 + " " + (arp._2 - arp._1) +
				" -" + arp._2 + " }")
			arps.put(arp._1 * 16 + arp._2, index)
			index += 1
		}
	}

	def printVibratoMacros(): Unit = {
		// Get a set of all 4xx that occur in the song
		var vibSet: Set[(Int, Int)] = Set()
		for (pattern <- xm.patterns) {
			for (row <- 0 to (pattern.numRows - 1)) {
				for (channel <- 0 to (xm.numChannels - 1)) {
					val effect = pattern.notes(row)(channel).effectType.toInt
					if (effect == 4) {
						val parameter = pattern.notes(row)(channel).effectParameter.toInt
						if (parameter != 0)
							vibSet += Pair((parameter & 0xf0) >> 4, parameter & 0xf)
					}
				}
			}
		}

		// Add instrument vibratos to the set
		for (instrument <- xm.instruments) {
			if (instrument.vibratoDepth != 0 && instrument.vibratoRate != 0) {
				vibSet += Pair(instrument.vibratoRate / 4, instrument.vibratoDepth / 4)
			}
		}

		// Print a macro for each 4xx
		var index = 0
		for (vib <- vibSet) {
			println("@MP" + index + " = { 0 " + vib._1 + " " + vib._2 + " }")
			vibratos.put(vib._1 * 16 + vib._2, index)
			index += 1
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

		printVolumeEnvelopes
		printArpMacros
		printVibratoMacros
		if (format == gb) {
			printPanningMacros
			printWaveformMacros
		} else if (format == c64) {
			printAdsrMacros
		}

		// Body

		var channelLetter = 'A'
		for (channel <- 0 to (numChannels - 1)) {
			print(channelLetter)

			lastNote = ""

			var currentPan = 0
			var currentVol = 0
			var currentIns = 0
			var currentPw = 0 // pulse width
			var currentAdsr = 0
			var currentDuty = 0
			var currentOctave = 0
			var currentEnvelope = 0
			var currentNote = 0
			var currentArp = -1
			var currentVib = -1
			var noteValue = -1

			if (xm.patterns(xm.orderTable(0)).notes(0)(channel).note.toInt == 0) {
				lastNote = "" + format.waitChar
			}

			for (patternNo <- xm.orderTable) {

				val pattern = xm.patterns(patternNo)
				for (row <- 0 to (pattern.numRows - 1)) {
					noteValue += 1

					var note = pattern.notes(row)(channel).note.toInt
					val volume = pattern.notes(row)(channel).volume.toInt
					var instrument = pattern.notes(row)(channel).instrument.toInt
					val effect = pattern.notes(row)(channel).effectType.toInt
					val parameter = pattern.notes(row)(channel).effectParameter.toInt

					val pan =
						if (effect == 8) {
							if (parameter < 128)
								-1
							else if (parameter == 128)
								0
							else
								1
						} else if (note != 0) {
							0
						} else {
							currentPan
						}

					val arp =
						if (effect == 0) {
							if (parameter == 0)
								-1
							else
								arps.get(parameter)
						} else if (note != 0) {
							-1
						} else {
							currentArp
						}

					val vib =
						if (effect == 4) {
							if (parameter == 0)
								-1
							else
								vibratos.get(parameter)
						} else if (note != 0) {
							if (instrument != 0 &&
									xm.instruments(instrument - 1).vibratoRate != 0 &&
									xm.instruments(instrument - 1).vibratoDepth != 0)
								vibratos.get(xm.instruments(instrument - 1).vibratoRate * 16 +
									xm.instruments(instrument - 1).vibratoDepth)
							else
								-1
						} else {
							currentVib
						}

					val vol =
						if (volume != 0) {
							min(format.maxVolume(channel), 
								(volume - 0x10) / (64 / format.maxVolume(channel)))
						} else if (note != 0) {
							format.maxVolume(channel)
						} else {
							currentVol
						}

					if (currentEnvelope > 0 && 
							!xm.instruments(currentEnvelope - 1).volumeOn && 
							vol != currentVol && note == 0) {
						note = currentNote
						instrument = currentEnvelope
					}

					if (note == 97) { // note off
						printNoteValue(noteValue)
						noteValue = 0
						print(" ")
						lastNote = "r"
					} else if (note != 0) { // note
						printNoteValue(noteValue)
						noteValue = 0
						currentNote = note

						if (format == gb && pan != currentPan) {
							currentPan = pan
							if (pan < 0)
								print(" CS0")
							else if (pan == 0)
								print(" CS1")
							else
								print(" CS2")
						}

						if (instrument != 0) {
							if (format == gb && channel == 2 && instrument != currentIns) {
								currentIns = instrument
								print(" WT" + instrument)
							}
	
							if (format == c64) {
								val pw = xm.instruments(instrument - 1).pulseWidth
								if (currentPw != pw) {
									currentPw = pw
									print(" pw" + currentPw)
								}
								if (currentAdsr != instrument) {
									currentAdsr = instrument
									print(" ADSR" + currentAdsr)
								}
							} else if (currentEnvelope != instrument) {
								currentEnvelope = instrument
								if (xm.instruments(instrument - 1).volumeOn)
									print(" @v" + instrument)
							}

							val duty = xm.instruments(instrument - 1).duty
							if (duty != currentDuty && 
									((format == gb && channel != 2) || (format == nes &&
									channel != 4) || (format == c64) || (format == sms &&
									channel == 3) || (format == cpc))) {
								currentDuty = duty
								print(" @" + currentDuty)
							}
						}

						var octave = (note - 1) / 12
						if (format == gb && channel == 3)
							octave += 4
						if (currentOctave != octave) {
							currentOctave = octave
							print(" o" + octave)
						}
						if (currentArp != arp) {
							currentArp = arp
							if (arp == -1)
								print(" ENOF")
							else
								print(" EN" + arp)
						}
						if (currentVib != vib) {
							currentVib = vib
							if (vib == -1)
								print(" MPOF")
							else
								print(" MP" + vib)
						}
						if (!xm.instruments(instrument - 1).volumeOn && vol != currentVol) {
							print(" v" + vol)
							currentVol = vol
						}
						print(" ")
						lastNote = pitchNames((note - 1) % 12)
					}
				}
			}
			printNoteValue(noteValue)
			println("")
			channelLetter = (channelLetter + 1).toChar
		}
	}
}

