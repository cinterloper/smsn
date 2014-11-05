/*
 * Monomanual Typeatron firmware, copyright 2013-2014 by Joshua Shinavier
 * See: https://github.com/joshsh/extendo and the Typeatron Mark 1 EAGLE schematic.
 *
 * D0:  Bluetooth RX 
 * D1:  Bluetooth TX
 * D2:  push button 1
 * D3:  vibration motor
 * D4:  push button 2
 * D5:  tactile transducer
 * D6:  laser
 * D7:  push button 3
 * D8:  push button 4
 * D9:  RGB LED red
 * D10: RGB LED green
 * D11: RGB LED blue
 * D12: push button 5
 * D13: LED
 * A0:  piezo motion sensor
 * A1:  (unused)
 * A2:  (unused)
 * A3:  photoresistor
 * A4:  I2C SDA for MPU-6050
 * A5:  I2C SCL for MPU-6050
 */

// OSC addresses
const char *EXO_TT_KEYS          = "/exo/tt/keys";
const char *EXO_TT_LASER_EVENT   = "/exo/tt/laser/event";
const char *EXO_TT_LASER_TRIGGER = "/exo/tt/laser/trigger";
const char *EXO_TT_MODE          = "/exo/tt/mode";
const char *EXO_TT_MORSE         = "/exo/tt/morse";
const char *EXO_TT_PHOTO_DATA    = "/exo/tt/photo/data";
const char *EXO_TT_PHOTO_GET     = "/exo/tt/photo/get";
const char *EXO_TT_PING          = "/exo/tt/ping";
const char *EXO_TT_PING_REPLY    = "/exo/tt/ping/reply";
const char *EXO_TT_RGB_SET       = "/exo/tt/rgb/set";
const char *EXO_TT_VIBRO         = "/exo/tt/vibro";

// these are global so that we can read from setup() as well as loop()
unsigned int keys[5];
unsigned int keyState;
unsigned int totalKeysPressed;
unsigned int lastKeyState = 0;


////////////////////////////////////////////////////////////////////////////////

#include <OSCBundle.h>
#include <ExtendOSC.h>

ExtendOSC osc("/exo/tt");

OSCBundle *bundleIn;

void sendError(const char *message) {
   osc.sendError(message);
}


////////////////////////////////////////////////////////////////////////////////

const int keyPin1 = 2;
const int keyPin2 = 4;
const int keyPin3 = 7;
const int keyPin4 = 8;
const int keyPin5 = 12;
const int vibrationMotorPin = 3;
const int transducerPin = 5;  
const int laserPin = 6;
const int ledPin =  13;

// note: blue and green pins were wired in reverse w.r.t. the BL-L515 datasheet
const int redPin = 9;
const int greenPin = 10;
const int bluePin = 11;

const int photoresistorPin = A3;


////////////////////////////////////////////////////////////////////////////////

// TODO: tailor the bounce interval to the switch being used.
// This 2ms value is a conservative estimate based on an average over many kinds of switches.
// See "A Guide to Debouncing" by Jack G. Ganssle
unsigned int debounceMicros = 2000;


////////////////////////////////////////////////////////////////////////////////

#include <AnalogSampler.h>

AnalogSampler photoSampler(photoresistorPin);


////////////////////////////////////////////////////////////////////////////////

#include <RGBLED.h>

RGBLED rgbled(redPin, greenPin, bluePin, sendError);

int colorToggle = 0;

void colorDebug() {
    if (keyState) {
        long modeColor = getModeColor();
        rgbled.replaceColor(modeColor);
    } else {
        rgbled.replaceColor(RGB_BLACK);
    }
}

void rgbForDuration(unsigned long color, unsigned long ms) {
    rgbled.pushColor(color);
    delay(ms);
    rgbled.popColor();
}


////////////////////////////////////////////////////////////////////////////////

#include <Droidspeak.h>

Droidspeak droidspeak(transducerPin);


////////////////////////////////////////////////////////////////////////////////

void laserOn() {
    digitalWrite(laserPin, HIGH); 
   
    // also turn on the on-board LED, as a cue to the developer in USB mode (when the laser is powered off)
    digitalWrite(ledPin, HIGH);
}

void laserOff() {
    digitalWrite(laserPin, LOW); 
   
    digitalWrite(ledPin, LOW);
}


////////////////////////////////////////////////////////////////////////////////

void vibrateForDuration(unsigned long ms) {
    digitalWrite(vibrationMotorPin, HIGH);
    delay(ms);
    digitalWrite(vibrationMotorPin, LOW);
}


////////////////////////////////////////////////////////////////////////////////

const int totalModes = 6;

typedef enum { 
    Text = 0,
    LaserTrigger,
    LaserPointer,
    Mash
} Mode;

const char *modeNames[] = {
    "Text",
    "LaserTrigger",
    "LaserPointer",
    "Mash"
};
    
const unsigned long modeColors[] = {
    RGB_BLUE,   // Text
    RGB_BLACK,  // LaserTrigger
    RGB_RED,    // LaserPointer
    RGB_WHITE   // Mash
};

Mode mode;

void setMode(int m) {
    mode = (Mode) m;
}

unsigned long getModeColor() {
    return modeColors[mode];      
}

int modeValueOf(const char *name) {
    int i;
    for (i = 0; i < totalModes; i++) {
        if (!strcmp(name, modeNames[i])) {
            osc.sendInfo("identified mode as %d", i);
            return i;
        }
    }
    
    osc.sendError("no such mode: %s", name);
    
    return Text;
}


////////////////////////////////////////////////////////////////////////////////

void readKeys() {
    keys[0] = !digitalRead(keyPin1);
    keys[1] = !digitalRead(keyPin2);
    keys[2] = !digitalRead(keyPin3);
    keys[3] = !digitalRead(keyPin4);
    keys[4] = !digitalRead(keyPin5);
    
    keyState = 0;
    totalKeysPressed = 0;
    
    for (int i = 0; i < 5; i++) {
        keyState |= keys[i] << i;  
        
        if (keys[i]) {
            totalKeysPressed++;
        }
    }  
}


////////////////////////////////////////////////////////////////////////////////

#include <Morse.h>

int morseStopTest() {
    // abort the playing of a Morse code sequence by pressing 3 or more keys at the same time
    return totalKeysPressed >= 3;
}

Morse morse(transducerPin, morseStopTest, sendError);


////////////////////////////////////////////////////////////////////////////////

// note: tones may not be played (via the Typeatron's transducer) in parallel with the reading of button input,
// as the vibration causes the push button switches to oscillate when depressed
void powerUpSequence() {
    rgbled.replaceColor(RGB_RED);
    droidspeak.speakPowerUpPhrase();
    rgbled.replaceColor(RGB_BLUE);
    digitalWrite(vibrationMotorPin, HIGH);
    delay(200);
    digitalWrite(vibrationMotorPin, LOW);
    rgbled.replaceColor(RGB_GREEN);
}

void setup() {
    pinMode(keyPin1, INPUT);
    pinMode(keyPin2, INPUT);     
    pinMode(keyPin3, INPUT);     
    pinMode(keyPin4, INPUT);     
    pinMode(keyPin5, INPUT);

    pinMode(vibrationMotorPin, OUTPUT);
    pinMode(transducerPin, OUTPUT);
    pinMode(laserPin, OUTPUT);
    pinMode(ledPin, OUTPUT);

    // take advantage of the Arduino's internal pullup resistors
    digitalWrite(keyPin1, HIGH);    
    digitalWrite(keyPin2, HIGH);    
    digitalWrite(keyPin3, HIGH);    
    digitalWrite(keyPin4, HIGH);    
    digitalWrite(keyPin5, HIGH);

    rgbled.setup();

    powerUpSequence();

    osc.beginSerial();
    droidspeak.speakSerialOpenPhrase();

    //setMode(LowercaseText);

    bundleIn = new OSCBundle();   
    
    //morse.playMorseString("foo");
    rgbled.replaceColor(RGB_BLACK);
}


////////////////////////////////////////////////////////////////////////////////

void handleOSCBundle(class OSCBundle &bundle) {
    if (bundle.hasError()) {
        osc.sendOSCBundleError(bundle);
    } else if (!(0
        || bundle.dispatch(EXO_TT_LASER_TRIGGER, handleLaserTriggerMessage)
        || bundle.dispatch(EXO_TT_MODE, handleModeMessage)
        || bundle.dispatch(EXO_TT_MORSE, handleMorseMessage)
        || bundle.dispatch(EXO_TT_PHOTO_GET, handlePhotoGetMessage)
        || bundle.dispatch(EXO_TT_PING, handlePingMessage)
        || bundle.dispatch(EXO_TT_RGB_SET, handleRGBSetMessage)
        || bundle.dispatch(EXO_TT_VIBRO, handleVibroMessage)
        )) {
        osc.sendError("no messages dispatched");
    }
}

void handleLaserTriggerMessage(class OSCMessage &m) {
    setMode(LaserTrigger); 
}

void handleModeMessage(class OSCMessage &m) {
    if (!osc.validArgs(m, 1)) return;

    if (m.isString(0)) {
        int length = m.getDataLength(0);
        char buffer[length+1];
        m.getString(0, buffer, length+1);
        
        setMode(modeValueOf(buffer));
    } else {
        osc.sendError("expected string-valued mode name");
    }
}

const int morseBufferLength = 32;
char morseBuffer[morseBufferLength];

void handleMorseMessage(class OSCMessage &m) {
    if (!osc.validArgs(m, 1)) return;

    int length = m.getDataLength(0);
    if (length >= morseBufferLength) {
        osc.sendError("Morse message is too long");
        return;
    } else {
        m.getString(0, morseBuffer, length+1);
osc.sendInfo("handle morse here: %s", morseBuffer);
        //morse.playMorseString((const char*) morseBuffer);  
    }
    //char buffer[length+1];
    //m.getString(0, buffer, length+1);
    //morse.playMorseString(buffer);
}

void handlePhotoGetMessage(class OSCMessage &m) {
    photoSampler.reset();
    photoSampler.beginSample();
    photoSampler.measure();
    photoSampler.endSample();
   
    sendLightLevel();
}

void handlePingMessage(class OSCMessage &m) {
    sendPingReply();
}

void handleRGBSetMessage(class OSCMessage &m) {
    if (!osc.validArgs(m, 1)) return;
  
    int32_t color = m.getInt(0);

    if (color < 0 || color > 0xffffff) {
        osc.sendError("color out of range: %d", (long) color);
    } else {
        rgbled.replaceColor(color);
    }
}

void handleVibroMessage(class OSCMessage &m) {
    if (!osc.validArgs(m, 1)) return;

    int32_t d = m.getInt(0);

    if (d <= 0) {
        osc.sendError("duration must be a positive number");
    } else if (d > 60000) {
        osc.sendError("duration too long");
    } else {
        vibrateForDuration((unsigned long) d);
    }
}

void sendAnalogObservation(class AnalogSampler &s, const char* address) {
    OSCMessage m(address);
    m.add((uint64_t) s.getStartTime());
    m.add((uint64_t) s.getEndTime());
    m.add((int) s.getNumberOfMeasurements());
    m.add(s.getMinValue());
    m.add(s.getMaxValue());
    m.add(s.getMean());
    m.add(s.getVariance());
 
    osc.sendOSC(m); 
}

void sendKeyEvent(const char *keys) {
    OSCMessage m(EXO_TT_KEYS);
    m.add(keys);

    osc.sendOSC(m);
}

void sendLightLevel() {
    sendAnalogObservation(photoSampler, EXO_TT_PHOTO_DATA);
}

void sendPingReply() {
    OSCMessage m(EXO_TT_PING_REPLY);
    m.add((uint64_t) micros());
    
    osc.sendOSC(m);
}

void sendLaserEvent() {
    OSCMessage m(EXO_TT_LASER_EVENT);
    m.add((uint64_t) micros());
    
    osc.sendOSC(m);
}


////////////////////////////////////////////////////////////////////////////////

void loop() {
    // SLIP+OSC serial input
    if (osc.receiveOSCBundle(*bundleIn)) {
        handleOSCBundle(*bundleIn);
        bundleIn->empty();
        delete bundleIn;
        bundleIn = new OSCBundle();
    }
    
    // keying action
    readKeys(); 
    if (keyState != lastKeyState) {
        colorDebug();

        if (LaserTrigger == mode) {
            if (keyState) {
                laserOn();
                sendLaserEvent();
                setMode(LaserPointer);
            }
        } else if (LaserPointer == mode) {
            if (!keyState) {
                setMode(Text);
                laserOff();
            }
        } else {       
            unsigned int before = micros();

            char keyStr[6];
            for (int i = 0; i < 5; i++) {
                keyStr[i] = keys[i] + 48;
            }
            keyStr[5] = 0;
    
            sendKeyEvent(keyStr);
    
            unsigned int after = micros();
            
            if (after - before < debounceMicros) {
                delayMicroseconds(debounceMicros - (after - before));
            }
        }
    } 
    lastKeyState = keyState;

    //int l = analogRead(A3);
    //System.out.print("light level: ");
    //System.out.println(l);
}

