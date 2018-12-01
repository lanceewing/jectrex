package emu.jectrex.video;

import emu.jectrex.io.Joystick;
import emu.jectrex.io.Via6522;

/**
 * This class emulates the video circuitry of the Vectrex, which doesn't have a video
 * chip as such but instead uses analog circuitry to control the position of the 
 * electron gun in the CRT. At the moment we're faking this by preparing a "frame" of 
 * video information, which is everything that has changed within the past 20 ms. The
 * emulator itself expects to refresh the screen at 50 Hz. 
 * 
 * @author Lance Ewing
 */
public class Video {

  // The Vectrex runs at 1.5 MHz (1/4 of the 6 MHz input freq to the cpu).
  private static final int CLOCK_FREQ = 1500000;//Hz
  private static final int FRAME_RATE = 50;//Hz
  private static final int CYCLES_PER_FRAME = (CLOCK_FREQ / FRAME_RATE);
  
  // 33000 is the width used in other emulators, but 32768 is a close-ish power of 2.
  // 41000 is the height used in other emulators, but 40960 is close enough and allows 
  // us to easily scale down to 1024x1280 or 512x640
  private static final int WIDTH       = 32768;        // (512 * 64) = 32768
  private static final int HEIGTH      = 40960;        // ((512 + 128 = 640) * 64) = 40960
  private static final int HALF_WIDTH  = WIDTH / 2;
  private static final int HALF_HEIGHT = HEIGTH / 2;
  // 40 x 1024 = 40960
  // 32 x 1024 = 32768
  // 40960 / 40 = 1024
  // 32960 / 40 = 824
  
  /**
   * An array of two Frames, one being the one that the video code is currently writing to,
   * the other being the last one that was completed and ready to blit.
   */
  private Frame[] frames;
  
  /**
   * Stores the current active phosphor dots on the screen.
   */
  private Phosphors phosphors;
  
  /**
   * The index of the active frame within the frames. This will toggle between 0 and 1.
   */
  private int activeFrame;
  
  /**
   * The current cycle count within the current frame.
   */
  private int currentCycleCount;
  
  /**
   * The Via6522 that controls the analog video.
   */
  private Via6522 via;

  /**
   * The Joystick that shares the Video circuitry's MUX.
   */
  private Joystick joystick;
  
  /**
   * Constructor for Video.
   * 
   * @param via The Via6522 that controls the analog video.
   * @param joystick The Joystick that shares the Video circuitry's MUX.
   */
  public Video(Via6522 via, Joystick joystick) {
    this.via = via;
    this.joystick = joystick;
    
    phosphors = new Phosphors();
    
    frames = new Frame[2];
    frames[0] = new Frame();
    frames[0].phosphors = phosphors;
    frames[0].ready = false;
    
    frames[1] = new Frame();
    frames[1].phosphors = phosphors;
    frames[1].ready = false;
  }
  
  //  1) The DAC. (Digital to Analog Convertor)
  //
  //  This is the most crucial part of the Vector block. It is connected
  //  directly to PORT A of the 6522. (See 6522A Section for details
  //  on how to setup and write to PORT A). The DAC takes this 8 bit
  //  digital value and converts it into an analog signal proportional
  //  to the value of the byte presented to the DAC.
  //
  //          DAC Vout = (PORTA - OFFSET) * Constant
  //
  //  The range if the DAC output if from +?.??V to -?.??V in steps
  //  of ?.??V.
  //
  //  Be warned there is NO enable control on the DAC so whatever value
  //  you present will be converted. The DAC output connects to the
  //  input of the multiplexer and the input of the X Axis integrator.
  //  PORT A which feeds the DAC also connects to the AY-3-8192 so when
  //  you write to the sound chip make sure that either/both the BLANK
  //  and RAMP signals are not asserted, otherwise you may draw crap
  //  all over the CRT.
  //
  //
  //2) The MULTIPLEXER (Mixed Digital/Analog)
  //
  //  The output of the DAC is connected to the input of the Multiplexer
  //  IC (one half, the other half is used for AtoD conversion of the
  //  joypad potentiometers). The Multiplexer takes the input signal
  //  and switches to any one of FOUR output lines, the input signal
  //  is only ever connected to one output line at any one time.
  //
  //  The multiplexer has 3 control inputs. The first, the ENABLE signal
  //  comes from the 6522A SWITCH line from PORT B Bit0 (See the 6522A
  //  section on how to set/clear this bit). When this line is ACTIVE
  //  the input pin is connected to the selected output pin, just as
  //  if a mechanical switch were thrown, when NEGATED the input and
  //  output pins are completely isolated from each other.
  //
  //  The other two inputs SEL0 and SEL1, again from PORT B of the 6522
  //  Bits 1 & 2 respectively. These bits are used to form a 2 bit number
  //  in the range 0-3, and when the multiplexer is active (see above)
  //  these inputs are used to decide which ouput pin is connected to
  //  the input pin. The Pin/Channel numbers for the Vector multiplexer
  //  are given below:
  //
  //          0 - Y Axis integrator channel
  //
  //          1 - X,Y Axis integrator offset
  //
  //          2 - Z Axis (Vector Brightness) level
  //
  //          3 - Connected to sound output line via divider network
  //
  //
  //3) X-Axis integrator, Y-Axis integrators (Analog)
  //
  //  Before the input to each integrator is an analog switch, this
  //  switch is connected to the RAMP line from the 6522PIA PORT B
  //  Bit7. When the switch is closed (RAMP=0) the integrators will
  //  integrate the Value presented on the input, when RAMP is negated
  //  and the switch opened no integration action can occur and the
  //  integrators will hold their current value (see ZERO function).
  //  The outputs will follow the following form:
  //
  //
  //               ^
  //              |
  //         1    |
  //     -  ---   |  ( Vin - Voffset ) dT   + Constant
  //        CxR   |
  //              |
  //             v
  //
  //  For the X,Y integrators R=10000 and C=0.01x10E-6
  //
  //  When we fill the numbers in we get:
  //
  //
  //     Vout = - ((10000 x (Vin-Voffset) x Integration time) + Voutstart)
  //
  //
  //  Assuming that the full deflection voltages are +5V and -5V we can
  //  calculate the beam movement. This is always a relative movement
  //  based on the last position of the beam. If you want to be sure of
  //  where the beam is make sure you ZERO the integrators first then
  //  you can be sure the beam is at 0,0.
  //
  //
  //     IntInput  = (DAC value - 128) * (10/256)
  //
  //     IntOffset = (DAC value - 128) * (10/256)
  //
  //  The 10/256 comes from a 10 volt possible swing on the DAC in 256
  //  steps, therefore one step is 10/256 Volts. The -128 turns the DAC
  //  value into a plus/minus value.
  //
  //
  //     delta X = - ((10000 x (IntInput-IntOffset) x RAMPtime) + X)
  //      (or Y)
  //
  //           X = X + detla X
  //
  //  Assuming the vextrex usable screen area is 8 Inches which converts
  //  to 20.3 cm. This is equal to 10 volts of swing.
  //
  //  (Note 8 inches is a guess, I dont have a vectrex screen to measure
  //   so please correct me if I'm wrong, also these firgures dont take
  //   into accound any overdrive of the screen area, and will therefore
  //   be slightly incorrect, though by how much I wouldnt like to say)
  //
  //
  //       X pos = X (volts) * 2.03 (cm/volt)
  //        (or Y)
  //
  //  This value is again referenced to the 0,0 point in the centre
  //  of the screen.
  //
  //  The power supply to the integrator comes from the +5V and -5V
  //  analog power supply. No matter how long we integrate for we can
  //  never exceed the supply rails, if we integrate for too long then
  //  then integrator will saturate. Typicall the integration time, the
  //  length of the active RAMP pulse will be quite short, in the order
  //  of microseconds or milliseconds.
  //
  //  To move over the full range of swing (10volts) it will take ??
  //  Assuming Voffset=0 and Voutstart=0 and no saturation.
  //
  //
  //          10 = - 10000 x -10 X Integration time
  //
  //          Integration time = 100us = 0.1ms
  //
  //(1/10000)/(1/1500000)*128              = 19200
  //
  //
  //
  //  This is for drawing the largest possible vector, from one side
  //  of the screen to the other.
  //
  //
  //          Vout = -5V to +5V in both X and Y Axis
  //
  //
  //4) RAMP (Digital, Active Low)
  //
  //As mentioned above the RAMP signal controls the integrator integration
  //time described in the previous section. This line is an active low
  //signal and is connected to the 6522 PIA Port B Bit 7.
  //
  //
  //5) ZERO (Digital, Active Low)
  //
  //This line is connected to the 6522 CA2 line, see the 6522 section for
  //info on how to set this line up. As with RAMP is is an active low
  //signal. During any integration operation this line must be set to
  //the inactive state (HIGH).
  //
  //When ACTIVE this line will cause the integrator output to be set to
  //0V which will be the centre of the screen. This line should be
  //ACTIVATED before any line drawing sequence to set the integrators to
  //a known value. The integrator output value is held by a small capacitor
  //and this will leak charge, basically the result is that over a period
  //of time the when the integrator in not in RAMP mode its output will
  //slowly fall to zero. So after drawing a sequence of vectors it is best
  //to zero the output to give a solid reference.
  //
  //6) Z-Axis Signal (Analog)
  //
  //This signal controls the Z Axis of the CRT, the brightness. It is
  //set be setting the multiplexer to the Z-Axis setting and writing a
  //value to the DAC. This signal is connected to a Sample and Hold
  //circuit so you can switch the multiplexer away from this signal and
  //it will hold it value.
  //
  //As with the integrators (See 3,7) the sample and hold will slowly
  //drift towards zero, so every now and then in will need to be
  //re-written. A good idea would be to write this value once for each
  //object being drawn, or once per group of screen refresh if you are
  //using a constant brightness, of course if your vectors are of
  //differing brightness then you will need to set this value for each
  //vector anyway.
  //
  //7) BLANK (Digital, Active High)
  //
  //So that you dont need to change the brigness when positioning the
  //CRT beam on the Screen you can use this line to temporarily BLANK
  //off the Z-Axis signal, when NEGATED the Z-Axis signal will return
  //to its old value.
  //
  //
  //
  //Bringing it all together
  //
  //The main basis if the vector drawing as weve seen is the X and Y integrators,
  //the Z Axis brightness is a passive system and easily understood. Just in
  //case the technical bit has lost you here is an analogy to explain the
  //integrator system.
  //
  //We'll convert all of our electrical system to some household plumbing.
  //Voltages will convert to water pressure and current/charge to water
  //flow.
  //
  //So our DAC is a TAP and the value we write to is the water pressure
  //behind the tap. An integrator can be thought of as a water tank. The
  //screen deflection is measured by the level of the water in the tank.
  //
  //So when we set the DAC values we set the water pressure at the tank
  //inlet. The RAMP switch controls the ON/OFF of the tap. So the higher
  //the pressure (X,Y integrator values from the DAC) the faster the
  //water will flow into the tank and the faster the level will rise, ie
  //the water level is the vector position, we have two tanks X and Y.
  //
  //So you can see we have two ways of regulating the water levels, the
  //water pressure and the amount of time we leave the tap on for.
  //
  //Halfway down the side of each tank is a valve marked ZERO. We always
  //start with our tanks half full. And after we've poured our water in
  //we can open the ZERO valve to dump out excess water and bring ourselves
  //back to the zero point (the screen centre).
  //
  //But our system has a twist, we can set the input taps to a negatve pressure
  //and suck water from the tank, and when we open the ZERO valve water will
  //flood in to fill us back up to the mid point. Our system also has a
  //few minor problems, our tanks have holes in them and slowly let the water
  //out back down to the middle level, and the pressure we set on the Tap
  //slowly fades away, so we must always ZERO and set the Tank pressure
  //before a flush/fill cycle.
  //
  //Its in this manor that we can push the CRT beam around the screen to
  //create the vectors we require.
  //
  //
  //
  //
  //How to draw a Vector
  //~~~~~~~~~~~~~~~~~~~~
  //
  //Maybe after reading the above you now have a pretty good idea about
  //how to draw vectors, or maybe you werent really interested and skipped
  //it, so here is the info on how to draw a vector.
  //
  //
  //Step    1   Set the BLANK bit to 0, it should already be set to
  //          this to stop it dribbling crap on the screen when idle
  //
  //      2   Set the ZERO bit to 0 to clear the integrators
  //
  //      3   Set the RAMP bit to 1
  //
  //      4   Set the ZERO bit back to 1
  //
  //      5   Enable the Multiplexer and select Z-Axis
  //
  //      6   Write your Vector Brightness to the DAC
  //
  //      7   Setect the X,Y Axis integrator offset and write
  //          your value to the DAC, this can be used to scale
  //          the X,Y axis up/down.
  //
  //--->   8   Set the BLANK line according to wether this operation
  //|           is going to be a DRAW or POSITION operation
  //|
  //|       9   Select the Y-Axis to the multiplexer and write your
  //|           Y-Axis Vector Velocity value to the DAC.
  //|
  //|       10  Disable the Multiplexer
  //|
  //|loop   11  Write your X-Axis Vector Velocity to the DAC.
  //|
  //|       12  Set RAMP to 1
  //|
  //|   < Vector is now being drawn>
  //|
  //---    13  Set RAMP to 0
  //
  //  < Vector has finished >
  //
  //      14  Set BLANK to 0 to stop any more info being drawn on the
  //          CRT no that weve finished.
  //
  //The above can be run in a loop for each object or the whole screen, I dont
  //know how the EXEC ROM does it but I would run the 1-14 sequence once
  //for each object, ie the 8-13 loop for the components of the object. This
  //would ensure the repeatability of object positioning as each object is
  //always placed with reference to zero rather than the last object drawn.
  //If you dont do a zero its likely that errors will build up and your
  //objects may appear wobbly or jittery.
  //
  //Parts 5&6 of the sequence can be omitted if you are only positioning the
  //vector ready for drawing.
  //
  //Now comes the tricky bit, just how do we calculate the values for X and
  //Y velocity, X,Y integrator offset and how long do we make the RAMP
  //pulse.
  //
  //The LENGTH of the RAMP pulse is critical if the screen is to appear
  //stable, you MUST disable the interrupts during the vector drawing cycle
  //as an interrupt occuring when RAMP is active would alter the length of
  //the RAMP pulse.
  //
  //As I see it there are 3 possible ways to drive the system. All assuming
  //that X,Y integrator offset is constant, you can use is for zoom in/out
  //effects.
  //
  //
  //1) The most likely way the EXEC ROM does it (IMHO). We have a FIXED length
  //of RAMP pulse for ALL vectors. Then with the 8 bit DAC values we
  //have a 255 unit resolution on vectors in both the X and Y axis. This
  //doesnt mean the screen resolution os 256x256, depending on the values
  //used for RAMP time and XY integrator offset a 255 unit vector could
  //be extremely small OR very large, its because everything is done
  //relative to the last integrator position. I reckon this method involves
  //the least calculation. You fix RAMP and then set the unit length with
  //the XY integrator offset.
  //
  //2) We have a look-up table of X,Y values depending on the direction that
  //we want to move (you could calculate the values) and these are all
  //scaled for a single fixed unit length vector. We then have a choice of
  //either varying the RAMP length or XY integrator offset to procuce
  //the length of vector we required, we just multiply the values of X and Y
  //integrator settings for the fixed unit length by the length we require.
  //
  //3) The most complicated way. We can vary all of the variables, but god
  //only knows how we would calulate them, we would have four varibles.
  //
  //Solutions 1 and 2 look the most promising as they restrict the number
  //of variables we have to calculate. Using a fixed value of RAMP would
  //seem the easiest thing so that we avoid any problems of CPU cycle counting
  //to get the required RAMP settings, we would need good control of the
  //RAMP time resolution, which is awkward, though not impossible in software
  //expecially when there is no hardware timer.
  //
  //Without access to a Vectrex and some development tools to write some
  //test code I cannot test any of these THEORYs and work out conversions
  //from DAC values,times to Screen positions. As I dont know what the full
  //screen deflection voltages are, I would assume that the Max/Min integrator
  //values will corrspond to the Max/Min screen deflection values, or maybe
  //the Max/Min value is off screen, but by how much ??

  private int xAxisIntegrator = 128;
  private int yAxisSampleAndHold = 128;
  private int zAxisSampleAndHold = 128;
  private int xyIntegratorOffset = 128;
  private int deltaX;
  private int deltaY;
  
  /**
   * 
   * 
   * @return
   */
  public boolean emulateCycle() {
    boolean frameRenderComplete = false;
    
    // PB0 - SWITCH    Sample / Hold strobe. MUX switch Control, enables/disables the analog multiplexer, 0=Enabled, because its an INHIBIT signal.
    // PB1 - SEL0      Controls multiplexer channel select.
    // PB2 - SEL1      
    // PB7 - ~RAMP     Integrator ramp. This line controls part of the vector drawing hardware. It is an active LOW signal.
    //
    // CA2 - ~ZERO     Connected to the integrators that form part of the vector drawing hardware. This 
    //                 line will cause them to be zero'd (both X and Y) and has the effect of bringing 
    //                 the beam back to the centre of the CRT. It is an active LOW signal.
    //
    // CB2 - ~BLANK    This Active LOW signal is the BEAM ON/OFF signal to the Vector drawing hardware, and is used to hide
    //                 the beam when it is being positioned for re-draw.

    // These are the VIA settings usually made use of, so need to work:
    // VIA Timer 1 (One shot mode, PB7 enabled)
    // VIA Shift Register Mode 4 (Shift Out Under T2 Control) output to CB2 under control of phase 2 clock
    
    int viaPortB = via.getPortBPins();
    
    boolean ramp = (viaPortB & 0x80) == 0;
    boolean zero = (via.getCa2() == 0);
    boolean blank = (via.getCb2() == 0);
    
    // DAC output always reflects VIA port A. There is no enable.
    int dacOut = (via.getPortAPins() ^ 0x80);
    
    // DAC output always feeds through to X axis integrator. There is no sample and hold for X
    // and it doesn't pass through the multiplexer.
    xAxisIntegrator = dacOut;
    
    // If the MUX is enabled, update the selected channel.
    boolean muxEnabled = (viaPortB & 0x01) == 0;    
    int muxChannelSelect = ((viaPortB & 0x06) >> 1);
    if (muxEnabled) {
      switch (muxChannelSelect) {
        case 0: // Y axis sample and hold.
          yAxisSampleAndHold = dacOut;
          break;
          
        case 1: // X/Y axis integrator offset (ZERO REF)
          xyIntegratorOffset = dacOut;
          break;
          
        case 2: // Z axis sample and hold (brightness)
          if (dacOut > 128) {
            zAxisSampleAndHold = dacOut - 128;
          } else {
            zAxisSampleAndHold = 0;
          }
          break;
          
        default: // Ignore.
          break;
      }
    }

    // The MUX is shared by the Joystick direction circuitry.
    joystick.processMux(muxChannelSelect, dacOut);

    if (zero) {
      deltaX = -phosphors.gunX;
      deltaY = -phosphors.gunY;
    } else {
      if (ramp) {
        deltaX = this.xAxisIntegrator - this.xyIntegratorOffset;
        deltaY = this.yAxisSampleAndHold - this.xyIntegratorOffset;
      } else {
        deltaX = 0;
        deltaY = 0;
      }
    }
    
    phosphors.move(deltaX, deltaY, zAxisSampleAndHold, !blank);
    
    if (++currentCycleCount >= CYCLES_PER_FRAME) {
      currentCycleCount = 0;
      
      synchronized(frames) {
        // Mark the current frame as complete.
        frames[activeFrame].ready = true;
        
        // TODO: This frame toggle can happen multiple times between screen renders.
        
        // Toggle the active frame.
        activeFrame = ((activeFrame + 1) % 2);
        frames[activeFrame].ready = false;
      }
      
      frameRenderComplete = true;
    }
    
    return frameRenderComplete;
  }
  
  int maxY = Integer.MIN_VALUE, minY = Integer.MAX_VALUE, maxX = Integer.MIN_VALUE, minX = Integer.MAX_VALUE;
  
  /**
   * Represents the data for one video frame.
   */
  public class Frame {
    
    /**
     * Says whether this frame is ready to be blitted to the GPU.
     */
    public boolean ready;
    
    /**
     * Holds the phosphors for the CRT frame. Shared with other frames, due to persistence of phosphors.
     */
    public Phosphors phosphors;
  }
  
  /**
   * Holds all phosphor dots in the Vectrex CRT. Maintains a sliding window of the currently
   * active phosphors. The Video emulation adds new phosphors at the "add" position. The
   * MachineScreen renders dots from current fadePosition to the addPosition, then adjusts
   * the fadePosition.
   */
  public class Phosphors {
    
    public static final int NUM_OF_PHOSPHORS = 50000;
    
    private Phosphor[] dots;
    
    private boolean currentlyInLine;
    
    private int gunX;
    
    private int gunY;
    
    // Only altered by add method, which is only called by this Video class.
    private int addPosition;
    
    // Only latered by setFadePosition, which is only called by rendering code in MachineScreen.
    private int fadePosition;
    
    // The total number of Phosphors created since the emulator started. More for debug than anything else.
    long phosphorCount;
    
    Phosphors() {
      dots = new Phosphor[NUM_OF_PHOSPHORS];
      
      // We create all of the Phosphor objects at startup and then simply reuse them
      // in a sliding window. Avoids object creation and garbage collection overheads.
      for (int i=0; i < NUM_OF_PHOSPHORS; i++) {
        dots[i] = new Phosphor();
      }
    }

    void move(int moveByX, int moveByY, int brightness, boolean electronGunOn) {
      if (electronGunOn) {
        if (xyOnScreen()) {
          add(gunX, gunY, brightness, !currentlyInLine);
          currentlyInLine = true;
        } else {
          currentlyInLine = false;
        }
      } else {
        currentlyInLine = false;
      }
      
      this.gunX += moveByX;
      this.gunY += moveByY;
    }
    
    void add(int x, int y, int z, boolean start) {
      Phosphor phosphor = dots[addPosition];
      phosphor.x = (x >> 6);
      phosphor.y = (y >> 6);
      phosphor.origZ = phosphor.z = z;
      phosphor.start = start;
      // As soon as we adjust addPosition, the phosphor becomes active.
      addPosition = ((addPosition + 1) % NUM_OF_PHOSPHORS);
      phosphorCount++;   // TODO: Remove at some point. This is mainly for interest when debugging and optimising.
    }
    
    boolean xyOnScreen() {
      return (gunX >= -HALF_WIDTH) && (gunX < HALF_WIDTH) && (gunY >= -HALF_HEIGHT) && (gunY < HALF_HEIGHT);
    }
    
    public int getAddPosition() {
      return addPosition;
    }
    
    public int getFadePosition() {
      return fadePosition;
    }
    
    public Phosphor[] getDots() {
      return dots;
    }
    
    public void setFadePosition(int newFadePosition) {
      // TODO: Note that there can be gaps between fade position and add position where a phosphor has already faded (i.e. z == 0)
      fadePosition = (newFadePosition % NUM_OF_PHOSPHORS);
    }    
  }
  
  /**
   * Represents a single Phosphor dot in the Vectrex CRT.
   */
  public class Phosphor {
    
    public int x;
    public int y;
    public int z;
    
    public int origZ;
    
    public boolean start;
    
  }
  
  public Frame getFrame() {
    Frame frame = null;
    synchronized (frames) {
      Frame nonActiveFrame = frames[((activeFrame + 1) % 2)];
      if (nonActiveFrame.ready) {
        nonActiveFrame.ready = false;
        frame = nonActiveFrame;
        //System.out.print("dotCount: " + frame.phosphors.phosphorCount + ", ");
        frame.phosphors.phosphorCount = 0;
      }
    }
    return frame;
  }
}
