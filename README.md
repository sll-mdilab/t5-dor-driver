# DOR Driver
## Introduction
This application can be used to send HL7v2 ORU R01-messages to an (IHE) Device Observation Consumer over MLLP. It fulfills the role of Device Observation Reporter and is intended to be used to simulate medical devices for testing purposes. It reads messages from a regular file and sends them to the target host with a specified delay. It can repeat messages and run multiple sending threads in parallel. 

## Build

The application is written in Java version 8 and uses Gradle for automatic building and dependency management.
Assuming that Java EE 8 development kit is installed and exist on the PATH environment variable, the project can be built with the following command from the project root folder:

    ./gradlew build fatJar

This outputs a .jar-file into the `build/lib` directory.

## Usage
    java -cp dordriver.jar net.sllmdilab.dordriver.application.DorDriverApplication host port inputfile [number of messages] [delay in milliseconds] [number of threads]

Arguments:

* `host` - IP adress or hostname of DOC-server.
* `port` - Port number for MLLP of DOC-server.
* `inputfile` - File from which to read messages.
* `[number of messages]` - Number of messages to send for each thread. If larger than the number of messages in the input file, messages will be repeated from the beginning.
* `[delay in milliseconds]` - The time to wait between sending messages.
* `[number of threads]` - Number of sending threads to run in parallel.

Note that arguments within [] are optional.

## Input
The format of the input file is one or multiple plain text HL7-Messages separated by newlines or MLLP control blocks.

## Output
The application transmits the messages from the input file with minor alterations:

* The MSH-7 message timestamp will be set to the current date/time as the message is sent.
* The difference between the initial OBR-7 timestamp and the current date/time will be added to each consequent OBR-7, OBR-8 and OBX-14 fields. This is done in order to preserve time difference between messages.

## HL7 Message Template Filler

The HL7 Message Template Filler can be used to inject waveform data into a HL7 message template and save it to file. The data can be either generated using a mathematical model or provided using a CSV file. The file generated can then be used with the DOR Driver application. 

### Usage

    java -cp dordriver.jar net.sllmdilab.dordriver.generator.Hl7MessageTemplateFiller 
		(-s | --src) <srcPath>
		(-d | --dst) <dstPath>
		(-o | --data) <dataPath>
		(-p | --pulse-rate) <value>
		(-f | --sample-rate) <value>
		(-t | --msg-time-frame) <value>
		
**Options**

* `-s src` - Path of the template (Required)
* `-d dst` - Path of the generated hl7 message file
* `-o data` - Path to the data file in CSV format
* `-p pulse-rate` - Pulse rate in BPM
* `-f sample-rate` - Sample rate
* `-t msg-time-frame` - Time frame of messages in milliseconds. The data provided will be divided into multiple messages of this length.


### Instructions
The HL7 Message Template must conform with the HL7v2 standard and include place holders surrounded by `<>`, e.g. `<MY_PLACEHOLDER>`, where data should be injected. There are two different use cases:

**1. Using a data file**

If a data file is specified with `-o` the place holders in the template must correspond to the columns of the first line in the CSV file. Remaining lines will be concatenated separated with `^` and finally replace the place holder.

The data file could look something like following:

| TIMESTAMP | SAMPLES_SAT | RATE_SAT |
|------|---------------|------------|
| '2015-01-01T00:00:00.000+0000' |0.122|100|
| '2015-01-01T00:00:00.010+0000' |0.123| |
| '2015-01-01T00:00:00.020+0000' |0.382| |
| ... | ... | |


**2. Using a generator**

If the `-o` option is excluded, a mathematical model will be used to generate waveform data based on the options `-p`, `-f` and `-t`. Data will be generated for the following place holders:

* `DEVICE_ID`
* `START_TIME`
* `END_TIME`
* `RANGE_HIGH_(ECG1|ECG2|ECG3|SAT|ABP)`
* `RANGE_LOW_(ECG1|ECG2|ECG3|SAT|ABP)`
* `RATE_(ECG1|ECG2|ECG3|SAT|ABP)`
* `SAMPLES_(ECG1|ECG2|ECG3|SAT|ABP)`

