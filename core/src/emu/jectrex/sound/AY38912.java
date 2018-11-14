package emu.jectrex.sound;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.audio.AudioDevice;
import com.badlogic.gdx.utils.GdxRuntimeException;

import emu.jectrex.io.Via6522;

/**
 * This class emulates the AY-3-8912 PSG chip. It is based heavily on
 * code from Richard Wilson's excellent JEMU emulator and I thank Richard 
 * for the kind offer on his website allowing his code to be used in other
 * projects. Your emulation of the AY-3-8912 is quite impressive! :-)
 */
public class AY38912 {

  // The Vectrex runs at 1.5 MHz (1/4 of the 6 MHz input freq to the cpu).
  private static final int CLOCK_FREQ = 1500000;
  private static final int SAMPLE_RATE = 22050;
  private static final int CYCLES_PER_SAMPLE = (CLOCK_FREQ / SAMPLE_RATE);

  // Not entirely sure what these volume levels should be. With LEVEL_DIVISOR set to 4, 
  // and volumes A, B, and C all at 15, then max sample is at 32760, which is just under
  // the limit.
  private final static int LEVEL_DIVISOR = 4;
  private final static int[] VOLUME_LEVELS = {
    0x003C/LEVEL_DIVISOR, 0x0055/LEVEL_DIVISOR, 0x0079/LEVEL_DIVISOR, 0x00AB/LEVEL_DIVISOR, 
    0x00F1/LEVEL_DIVISOR, 0x0155/LEVEL_DIVISOR, 0x01E3/LEVEL_DIVISOR, 0x02AA/LEVEL_DIVISOR,
    0x03C5/LEVEL_DIVISOR, 0x0555/LEVEL_DIVISOR, 0x078B/LEVEL_DIVISOR, 0x0AAB/LEVEL_DIVISOR,
    0x0F16/LEVEL_DIVISOR, 0x1555/LEVEL_DIVISOR, 0x1E2B/LEVEL_DIVISOR, 0x2AAA/LEVEL_DIVISOR 
  };
  
  // Constants for index values into output, count, and period arrays.
  private static final int A = 0;
  private static final int B = 1;
  private static final int C = 2;
  private static final int NOISE = 3;
  private static final int ENVELOPE = 4;
  
  private int[] output;   // A, B, C and Noise
  private int[] count;    // A, B, C, Noise and Envelope counters
  private int[] period;   // A, B, C, Noise and Envelope periods
  
  // Channel volumes. Envelope volume takes effect depending on volume mode bit.
  private int volumeA;
  private int volumeB;
  private int volumeC;
  private int volumeEnvelope;
  
  // Current mixer disable/enable settings. 
  private int enable;
  private boolean disableToneA;
  private boolean disableToneB;
  private boolean disableToneC;
  private boolean disableAllNoise;
  
  private int outNoise;
  private int random = 1;
  
  private int countEnv;
  private int hold;
  private int alternate;
  private int attack;
  private int holding;
  
  private int updateStep;
  private final int step = 0x8000;

  private int busControl1 = 0;
  private int busDirection = 0;
  private int addressLatch = 0;
  private int[] registers;

  private short[] sampleBuffer;
  private int sampleBufferOffset = 0;
  private int cyclesToNextSample;
  private AudioDevice audioDevice;
  
  /**
   * The AY-3-8912 in the Vectrex gets its data from the 6522 VIA chip.
   */
  private Via6522 via;
  
  /**
   * Constructor for AY38912.
   */
  public AY38912() {
  }

  /**
   * Constructor for AY38912PSG.
   * 
   * @param via The 6522 VIA chip that the register data comes from.
   */
  public AY38912(Via6522 via) {
    init(via);
  }
  
  /**
   * Initialise the AY38912 PSG.
   * 
   * @param via The 6522 VIA chip that the register data comes from.
   */
  public void init(Via6522 via) {
    this.via = via;
    
    updateStep = (int) (((long)step * 8L * (long)SAMPLE_RATE) / (long)CLOCK_FREQ);
    output = new int[] { 0, 0, 0, 0xFF };
    count  = new int[] { updateStep, updateStep, updateStep, 0x7fff, updateStep };
    period = new int[] { updateStep, updateStep, updateStep, updateStep, 0 };
    registers = new int[16];
    
    volumeA = volumeB = volumeC = volumeEnvelope = 0;
    disableToneA = disableToneB = disableToneC = disableAllNoise = false;
    countEnv = hold = alternate = attack = holding = 0;
    enable = 0;
    outNoise = 0;
    random = 1;
    busControl1 = 0;
    busDirection = 0;
    addressLatch = 0;
    
    int audioBufferSize = ((((SAMPLE_RATE/ 20) * 2) / 10) * 10);
    sampleBuffer = new short[audioBufferSize / 10];
    sampleBufferOffset = 0;
    
    try {
      audioDevice = Gdx.audio.newAudioDevice(SAMPLE_RATE, true);
    } catch (GdxRuntimeException e) {
      audioDevice = null;
    }
    
    cyclesToNextSample = CYCLES_PER_SAMPLE;
  }
  
  /**
   * Emulates a single cycle of activity for the AY-3-8912. This involves responding
   * to the CA2 and CB2 lines coming in from the 6522 VIA chip
   */
  public void emulateCycle() {
    int viaPortB = via.getPortBPins();
    
    // Bus Control 1 is connected to the VIA Port B bit 3 line in the Vectrex.
    busControl1 = (viaPortB & 0x08) >> 3;
    
    // Bus Direction is connected to the VIA Port B bit 4 line in the Vectrex.
    busDirection = (viaPortB & 0x10) >> 4;

    if (busDirection == 1) {
      if (busControl1 == 1) {
        // Address latch write is occurring.
        addressLatch = (via.getPortAPins() & 0x0f);

      } else {
        // Write register. Probably needs a "has it changed" check.
        writeRegister(addressLatch, via.getPortAPins());
      }
    } else {
      if (busControl1 == 1) {
        // Read register. In the Vectrex, this will be needed for reading the controller switches.
        via.setPortAPins(readRegister(addressLatch));
      }
    }
    
    // If enough cycles have elapsed since the last sample, then output another.
    if (--cyclesToNextSample <= 0) {
      writeSample();
      cyclesToNextSample += CYCLES_PER_SAMPLE;
    }
  }
  
  /**
   * Pauses the sound output. Invoked when the Machine is paused.
   */
  public void pauseSound() {
    // For libgdx, there is no pause sound.
  }

  /**
   * Resumes the sound output. Invoked when the Machine is unpaused.
   */
  public void resumeSound() {
    // For libgdx, there is no resume sound.
  }

  /**
   * Stops and closes the audio line.
   */
  public void dispose() {
    if (audioDevice != null) {
      audioDevice.dispose();
    }
  }
  
  /**
   * Gets the current value stored in PORT A. The Keyboard instance will call this
   * method to get the selected columns when testing if key(s) are pressed.
   * 
   * @return The current value stored in PORT A (the AY-3-8912 only has one port).
   */
  public int getIOPortA() {
    return registers[14];
  }
  
  /**
   * Reads the value stored in an AY-3-8912 register.
   * 
   * @param address The address of the register to read from.
   * 
   * @return The value stored in the register.
   */
  public int readRegister(int address) {
    return registers[address];
  }

  /**
   * Writes a value to an AY-3-8912 register.
   * 
   * @param address The address of the register to write to.
   * @param value The value to write to the register.
   */
  public void writeRegister(int address, int value) {
    registers[address] = value;

    switch (address) {
    
      case 0x00:    // Fine tune A
      case 0x01:    // Coarse tune A
      case 0x02:    // Fine tune B
      case 0x03:    // Coarse tune B
      case 0x04:    // Fine tune C
      case 0x05: {  // Coarse tune C
        address >>= 1;
        int val = (((registers[(address << 1) + 1] & 0x0f) << 8) | registers[address << 1]) * updateStep;
        int last = period[address];
        period[address] = val = ((val < 0x8000)? 0x8000 : val);
        int newCount = count[address] - (val - last);
        count[address] = newCount < 1 ? 1 : newCount;
        break;
      }

      // Noise period.
      case 0x06: { 
        int val = (value & 0x1f) * updateStep;
        val *= 2;
        int last = period[NOISE];
        period[NOISE] = val = val == 0 ? updateStep : val;
        int newCount = count[NOISE] - (val - last);
        count[NOISE] = newCount < 1 ? 1 : newCount;
        break;
      }

      // Voice enable (i.e Mixer)
      case 0x07:
        enable = value;
        disableToneA = (enable & 0x01) != 0;
        disableToneB = (enable & 0x02) != 0;
        disableToneC = (enable & 0x04) != 0;
        disableAllNoise = (enable & 0x38) == 0x38;
        break;

      // Channel A volume
      case 0x08:
        volumeA = (((value & 0x10) == 0)? value & 0x0f : volumeEnvelope);
        break;
        
      // Channel B volume
      case 0x09:
        volumeB = (((value & 0x10) == 0)? value & 0x0f : volumeEnvelope);
        break;
        
      // Channel C volume
      case 0x0A:
        volumeC = (((value & 0x10) == 0)? value & 0x0f : volumeEnvelope);
        break;

      // Envelope Fine & Coarse tune
      case 0x0B:
      case 0x0C: {
        int val = (((registers[0x0C] << 8) | registers[0x0B]) * updateStep) << 1;
        int last = period[ENVELOPE];
        period[ENVELOPE] = val;
        int newCount = count[ENVELOPE] - (val - last);
        count[ENVELOPE] = newCount < 1 ? 1 : newCount;
        break;
      }

      // Envelope shape
      case 0x0D: {
        attack = (value & 0x04) == 0 ? 0 : 0x0f;
        if ((value & 0x08) == 0) {
          hold = 1;
          alternate = attack;
        } else {
          hold = value & 0x01;
          alternate = value & 0x02;
          if (hold != 0) {
            attack = alternate;
          }
        }
        count[ENVELOPE] = period[ENVELOPE];
        countEnv = 0x0f;
        holding = 0;
        int vol = volumeEnvelope = attack ^ 0x0f;
        if ((registers[0x08] & 0x10) != 0) {
          volumeA = vol;
        }
        if ((registers[0x09] & 0x10) != 0) {
          volumeB = vol;
        }
        if ((registers[0x0A] & 0x10) != 0) {
          volumeC = vol;
        }
        break;
      }
      
      default:
        break;
    }
  }

  /**
   * Writes a single sample to the sample buffer. If the buffer is full after writing the
   * sample, then the whole buffer is written out to the SourceDataLine.
   */
  public void writeSample() {
    if (disableToneA) {
      if (count[A] <= step) {
        count[A] += step;
      }
      output[A] = 1;
    }
    if (disableToneB) {
      if (count[B] <= step) {
        count[B] += step;
      }
      output[B] = 1;
    }
    if (disableToneC) {
      if (count[C] <= step) {
        count[C] += step;
      }
      output[C] = 1;
    }
    outNoise = output[NOISE] | enable;
    if (disableAllNoise) {
      if (count[NOISE] <= step) {
        count[NOISE] += step;
      }
    }
    
    int[] cnt = new int[3];
    int left = step;
    do {
      int add = count[NOISE] < left ? count[NOISE] : left;
      for (int channel = A; channel <= C; channel++) {
        int channelCount = count[channel];
        if ((outNoise & (0x08 << channel)) != 0) {
          int val = output[channel] == 0 ? cnt[channel] : cnt[channel] + channelCount;
          if ((channelCount -= add) <= 0) {
            int channelPeriod = period[channel];
            while (true) {
              if ((channelCount += channelPeriod) > 0) {
                if ((output[channel] ^= 0x01) != 0) {
                  val += channelPeriod - channelCount;
                }
                break;
              }
              val += channelPeriod;
              if ((channelCount += channelPeriod) > 0) {
                if (output[channel] == 0) {
                  val -= channelCount;
                }
                break;
              }
            }
          } else if (output[channel] != 0) {
            val -= channelCount;
          }
          cnt[channel] = val;
        } else {
          if ((channelCount -= add) <= 0) {
            int channelPeriod = period[channel];
            while (true) {
              if ((channelCount += channelPeriod) > 0) {
                output[channel] ^= 0x01;
                break;
              }
              if ((channelCount += channelPeriod) > 0) {
                break;
              }
            }
          }
        }
        count[channel] = channelCount;
      }

      if ((count[NOISE] -= add) <= 0) {
        int val = random + 1;
        if ((val & 0x02) != 0) {
          outNoise = (output[NOISE] ^= 0xff) | enable;
        }
        random = (random & 0x01) == 0 ? random >> 1 : (random ^ 0x28000) >> 1;
        count[NOISE] += period[NOISE];
      }

      left -= add;
    } while (left > 0);

    if (holding == 0 && period[ENVELOPE] != 0) {
      if ((count[ENVELOPE] -= step) <= 0) {
        int ce = countEnv;
        int envelopePeriod = period[ENVELOPE];
        do {
          ce--;
        } while ((count[ENVELOPE] += envelopePeriod) <= 0);

        if (ce < 0) {
          if (hold != 0) {
            if (alternate != 0) {
              attack ^= 0x0f;
            }
            holding = 1;
            ce = 0;
          } else {
            if (alternate != 0 && (ce & 0x10) != 0) {
              attack ^= 0x0f;
            }
            ce &= 0x0f;
          }
        }
        countEnv = ce;
        int vol = volumeEnvelope = ce ^ attack;
        if ((registers[0x08] & 0x10) != 0) {
          volumeA = vol;
        }
        if ((registers[0x09] & 0x10) != 0) {
          volumeB = vol;
        }
        if ((registers[0x0A] & 0x10) != 0) {
          volumeC = vol;
        }
      }
    }
    
    int sample =  (((((VOLUME_LEVELS[volumeA] * cnt[A]) >> 13) + 
                     ((VOLUME_LEVELS[volumeB] * cnt[B]) >> 13) + 
                     ((VOLUME_LEVELS[volumeC] * cnt[C]) >> 13)) & 0x7FFF));
    
    sampleBuffer[sampleBufferOffset + 0] = (short)sample;
    
    // If the sample buffer is full, write it out to the audio line.
    if ((sampleBufferOffset += 1) == sampleBuffer.length) {
      try {
        audioDevice.writeSamples(sampleBuffer, 0, sampleBuffer.length);
      } catch (Throwable e) {
        // An Exception or Error can occur here if the app is closing, so we catch and ignore.
      }
      sampleBufferOffset = 0;
    }
  }
}
