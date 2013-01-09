XM2MML User Manual
==================

Latest revision Jan 8, 2013.


Overview
========

    This program is designed to convert XM files into XPMCK-compatible MML,
allowing the user to create music for specific video game consoles or computers
without needing to use a tracker specific to that system--and without needing
to write MML.

The current systems "completely" supported by XM2MML are:
    - Nintendo Gameboy / Gameboy Color
    - SEGA Master System (PSG only)

The current systems "partially" supported by XM2MML are:
    - Nintendo Entertainment System (2a03 only)
    - Commodore 64

    In order to use XM2MML profitably, the user will need XPMCK (or MCK, in the
case of the NES). XPMCK can be downloaded at http://jiggawatt.org/muzak/xpmck/,
and MCK can be used at http://www.mmlshare.com/. This user manual will not
attempt to explain the operation of XPMCK or MCK, since those programs have
their own documentation. In short, these programs are used to compile specially
formatted MML into music files.


Usage
=====

    XM2MML is a command-line program; that is, it does not have a graphical
user interface. Invocations of the program on the command-line have the form:

    xm2mml <infile.xm>

    -OR-

    xm2mml <format> <infile.xm>

Replace <format> with the name of the format you would like to convert to, and
replace <infile.xm> with the filename of the source XM file. If no format is
specified, the program outputs generic MML. The program prints its output to
the terminal, so in practice most invocations of XM2MML will have the form:

    xm2mml <format> <infile.xm> > <outfile.mml>

Replace <outfile.mml> with the filename you would like the MML file to have.


Nintendo Gameboy / Gameboy Color
================================

Format names: gb, gbc, gbs, gameboy

Channels:
    - A (square wave), octaves 2-7
    - B (square wave), octaves 2-7
    - C (wavetable), octaves 1-7
    - D (noise), octaves 0-7

XM2MML supports the following features with the Gameboy:
    - Volume (using the volume column)
    - Duty cycles for channels A and B (using instrument names)
    - Noise type for channel D (using instrument names)
    - Waveforms for channel C (using sample data)
    - Panning (using 8xx)

    The duty cycle of an instrument can be set by putting @x in the
instrument's name. If x = 0, duty cycle is 12.5%; if x = 1, duty cycle is 25%;
if x = 2, duty cycle is 75%; if x = 4, duty cycle is 75%. Duty cycles are only
applicable to channels A and B. In channel D, if x = 0, white noise is used; if
x = 1, periodic noise is used. Duty cycle defaults to 12.5% and noise type
defaults to white noise if no @x is specified.

    When an instrument is used in channel C, XM2MML will generate a Gameboy
waveform for it based on that instrument's sample data. The sample should be
32 bytes long if 8-bit, or 64 bytes long if 16-bit. Longer or shorter samples
will work, but their waveforms will not be accurate.

    The effect 8xx can be used for panning on any channel. The Gameboy only
supports hard panning, so 880 sets panning to center, anything less than 80
sets panning to left, and anything greater than 80 sets panning to right.


Nintendo Entertainment System
=============================

Format names: nes, nsf, nintendo

Channels:
    - A (square wave), octaves 2-7
    - B (square wave), octaves 2-7
    - C (triangle wave), octaves 2-7
    - D (noise), any octave
    - E (DPCM), octaves 0-3

XM2MML supports the following features with the NES:
    - Volume for channels A, B, and D (using the volume column)
    - Duty cycles for channels A and B (using instrument names)
    - Noise type for channel D (using instrument names)

    NES duty cycles and noise type work as described for the Gameboy.

    DPCM is not yet implemented.


Commodore 64
============

Format names: c64, commodore

Channels:
    - A, B, and C (all alike), any octave

XM2MML supports the following features with the C64:
    - ADSR envelopes (using instrument volume envelopes)
    - Waveform settings (using instrument names)
    - Pulse width for square waves (using instrument names)

    ADSR envelopes for instruments are generated based on the volume envelopes
of instruments. If an instrument does not have a volume envelope, the ADSR for
that instrument will be { 15 0 15 0 }. For an ADSR envelope to be generated for
an instrument, its volume enveloped must start at 0, increase to maximum,
decrease to the sustain level, then decrease to 0.

    The waveform of an instrument can be set by putting @x in the instrument's
name. If x = 0, waveform is triangle; if x = 1, waveform is saw; if x = 2,
waveform is square; if x = 3, waveform is noise.

    The pulse width of a square wave (@2) instrument can be set by putting pwxx
in the instrument's name. xx can be a decimal integer from 0 to 15.


SEGA Master System
==================

Format names: sms

Channels:
    - A (square wave), octaves 2-7 (lowest note A2)
    - B (square wave), octaves 2-7 (lowest note A2)
    - C (square wave), octaves 2-7 (lowest note A2)
    - D (noise), octaves 1-7

XM2MML supports the following features with the Master System:
    - Noise type for channel D (using instrument names)

    The noise type for the Master Systems works as described for the Gameboy.
