/*
  Droidspeak.cpp
  Created by Joshua Shinavier, 2012-2014
  Released into the public domain.
*/

#include <math.h>

#include "Droidspeak.h"


Droidspeak::Droidspeak(uint8_t speakerPin)
{
    _speakerPin = speakerPin;
}

void Droidspeak::glideLinear(unsigned long duration, long startFrequency, long endFrequency)
{
    long diff = endFrequency - startFrequency;
    long steps = 100;
    unsigned long inc = (duration * 1000) / steps;
    
    for (long i = 0; i < steps; i++) {
      int t = startFrequency + (diff * i) / steps;
      tone(_speakerPin, t);
      delayMicroseconds(inc);
    }  
    
    noTone(_speakerPin);
}

void Droidspeak::glideLog(unsigned long duration, long startFrequency, long endFrequency)
{
    long steps = 100;
    unsigned long inc = (duration * 1000) / steps;
    double base = pow((double) endFrequency / (double) startFrequency, 1 / (double) (duration * 1000));
  
    for (long i = 0; i < steps; i++)
    {
        double t = i * inc;
        double f = startFrequency * pow(base, t);
        tone(_speakerPin, (int) f);
        delayMicroseconds(inc);
    }
    
    noTone(_speakerPin);
}

void Droidspeak::speakRandomSequence()
{
  int base = 440;
  
  // Using a pentatonic scale makes these random sequences more melodious
  double fact = 1.122462;
  
  for (int i = 0; i < 5; i++)
  {
    int x = random(0, 12);
    double t = base;
    for (int i = 0; i < x; i++)
    {
      t *= fact;
    }
  
    tone(_speakerPin, (int) t);
    delay(90); 
    noTone(_speakerPin);
    delay(10);
  }
}

void Droidspeak::speakOK()
{
  /*
    tone(_speakerPin, 330);
    delay(100);
    tone(_speakerPin, 262);
    delay(300);
    noTone(_speakerPin);
    delay(50);
    */
    
    glideLog(50, 196, 784);
    delay(20);
    glideLog(300, 1568, 49);
    delay(50);
}

void Droidspeak::speakPowerUpPhrase()
{
    glideLog(1000, 55, 14080);
    delay(50);
}

void Droidspeak::speakSerialOpenPhrase()
{
    //delay(100);
    //speakOK();

    speakRandomSequence();
    delay(50);
}

void Droidspeak::speakShockPhrase()
{
    glideLog(100, 14080, 55);
}

void Droidspeak::speakWarningPhrase()
{
    //tone(_speakerPin, 220);
    glideLog(20, 220, 880);
    //delay(100);
    glideLog(20, 880, 220);
    //delay(50);
    //glideLog(100, 220, 880);
}

// Note: a single-cycle tick is barely audible (using the Sanco EMB-3008A speaker),
// just enough to hear with your ear next to the device.
// Ten cycles produces a more noticeable, though still quiet, click.
// Even ten cycles takes practically no time (compared to a serial write operation).
void Droidspeak::tick()
{
    for (int i = 0; i < 10; i++) {
        digitalWrite(_speakerPin, HIGH);
        digitalWrite(_speakerPin, LOW);
    }
    
    //tone(_speakerPin, 440);
    //delayMicroseconds(10);
    //noTone(_speakerPin);
}