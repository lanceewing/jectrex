package emu.jectrex;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.files.FileHandle;

import emu.jectrex.config.AppConfigItem.FileLocation;
import emu.jectrex.cpu.Cpu6809;
import emu.jectrex.io.Joystick;
import emu.jectrex.io.Via6522;
import emu.jectrex.memory.Memory;
import emu.jectrex.sound.AY38912;
import emu.jectrex.video.Video;
import emu.jectrex.video.Video.Frame;

/**
 * Represents the Vectrex machine.
 * 
 * @author Lance Ewing
 */
public class Machine {

  // Machine components.
  private Memory memory;
  private Video video;
  private Via6522 via;
  private AY38912 psg;
  private Cpu6809 cpu;
  
  // Peripherals.
  private Joystick joystick;

  private boolean paused = true;
  private boolean lastWarpSpeed = false;
  
  // These control what part of the generate pixel data is rendered to the screen. 
  private int screenLeft;
  private int screenRight;
  private int screenTop;
  private int screenBottom;
  private int screenWidth;
  private int screenHeight;
  
  /**
   * Constructor for Machine.
   */
  public Machine() {
  }
  
  /**
   * Initialises the machine. It will boot into the default Vectrex EXEC ROM and Mine Storm.
   */
  public void init() {
    init(null, null, null);
  }
  
  /**
   * Initialises the machine, and optionally loads the given program file (if provided).
   * 
   * @param programFile The internal path to the program file to automatically load and run.
   * @param programType The type of program data, e.g. TAPE, etc.
   * @param fileLocation The location of the file, e.g. internal, external, local, classpath, absolute.
   */
  public void init(String programFile, String programType, FileLocation fileLocation) {
    byte[] programData = null;
    FileHandle fileHandle = null;
    
    // If we've been given the path to a program to load, we load the data prior to all
    // other initialisation.
    if ((programFile != null) && (programFile.length() > 0)) {
      try {
        fileLocation = (fileLocation != null? fileLocation : FileLocation.INTERNAL);
        switch (fileLocation) {
          case INTERNAL:
            fileHandle = Gdx.files.internal(programFile);
            break;
          case EXTERNAL:
            fileHandle = Gdx.files.external(programFile);
            break;
          case LOCAL:
            fileHandle = Gdx.files.local(programFile);
            break;
          case ABSOLUTE:
            fileHandle = Gdx.files.absolute(programFile);
            break;
          case CLASSPATH:
            fileHandle = Gdx.files.classpath(programFile);
            break;
        }
        if (fileHandle != null) {
          if (fileHandle.exists()){
            programData = fileHandle.readBytes();
          }
        }
      } catch (Exception e) {
        // Continue with default behaviour, which is to boot in to BASIC.
      }
    }
    
    // Create the microprocessor.
    cpu = new Cpu6809();
     
    // Create the peripherals.
    joystick = new Joystick();
    
    // Create the VIA chip.
    via = new Via6522(cpu, joystick);
    
    // Create the analog vector video component.
    video = new Video(via, joystick);
    
    // Initialise the AY-3-8912 PSG
    psg = new AY38912(via, joystick);
    
    // Now we create the memory, which will include mapping the VIA chip, and 
    // the creation of RAM chips and ROM chips.
    memory = new Memory(cpu, via);
    
    // Set up the screen dimensions based on aspect ratio of 5:4.
    screenWidth = ((MachineScreen.SCREEN_HEIGHT / 4) * 5);
    screenHeight = MachineScreen.SCREEN_HEIGHT;
    screenLeft = 0;
    screenRight = screenLeft + MachineScreen.SCREEN_WIDTH;
    screenTop = 0;
    screenBottom = screenTop + MachineScreen.SCREEN_HEIGHT;

    // Check if the resource parameters have been set.
    if ((programData != null) && (programData.length > 0)) {
      if ("CART".equals(programType)) {
        // Loads the cartridge ROM file into memory.
        memory.loadCustomRom(0x0000, programData);
      }
    }
    
    cpu.reset();
  }
  
  /**
   * Updates the state of the machine of the machine until a "frame" is complete
   * 
   * @param warpSpeed true If the machine is running at warp speed.
   */
  public void update(boolean warpSpeed) {
    boolean frameComplete = false;
    if (warpSpeed && !lastWarpSpeed) {
      // We pause sound during warp speed
      psg.pauseSound();
    } else if (lastWarpSpeed && !warpSpeed) {
      // And resume sound when warp speed ends.
      psg.resumeSound();
    }
    
    // TODO: Remove
    psg.pauseSound();
    
    lastWarpSpeed = warpSpeed;
    do {
      // The concept of a frame doesn't apply to the Vectrex, but we still refresh the 
      // screen at a rate of 50 Hz, so require that the video component track this for us.
      frameComplete |= video.emulateCycle();
      cpu.emulateCycle();
      via.emulateCycle();
      if (!warpSpeed) {
        psg.emulateCycle();
      }
    } while (!frameComplete);
  }
  
  /**
   * Gets whether the last frame was updated at warp speed, or not.
   * 
   * @return true if the last frame was updated at warp speed; otherwise false.
   */
  public boolean isLastWarpSpeed() {
    return lastWarpSpeed;
  }
  
  /**
   * @return the screenLeft
   */
  public int getScreenLeft() {
    return screenLeft;
  }

  /**
   * @return the screenRight
   */
  public int getScreenRight() {
    return screenRight;
  }

  /**
   * @return the screenTop
   */
  public int getScreenTop() {
    return screenTop;
  }

  /**
   * @return the screenBottom
   */
  public int getScreenBottom() {
    return screenBottom;
  }

  /**
   * @return the screenWidth
   */
  public int getScreenWidth() {
    return screenWidth;
  }

  /**
   * @return the screenHeight
   */
  public int getScreenHeight() {
    return screenHeight;
  }

  /**
   * Gets the currently ready "frame" from the Video circuitry emulation.
   *  
   * @return The currently ready Frame. Returns null if there isn't a Frame that is ready yet.
   */
  public Frame getFrame() {
    return video.getFrame();
  }
  
  /**
   * Emulates a single machine cycle.
   * 
   * @return true If the video chip has indicated that a frame should be rendered.
   */
  public boolean emulateCycle() {
    boolean render = video.emulateCycle();
    cpu.emulateCycle();
    via.emulateCycle();
    return render;
  }
  
  /**
   * Pauses and resumes the Machine.
   * 
   * @param paused true to pause the machine, false to resume.
   */
  public void setPaused(boolean paused) {
    this.paused = paused;
    
    // Pass this on to the AY-3-8912 so that it can stop the SourceDataLine.
    if (paused) {
      this.psg.pauseSound();
    } else {
      this.psg.resumeSound();
    }
  }
  
  /**
   * Returns whether the Machine is paused or not.
   * 
   * @return true if the machine is paused; otherwise false.
   */
  public boolean isPaused() {
    return paused;
  }
  
  /**
   * Invoked when the Machine is being terminated.
   */
  public void dispose() {
    // Tell the PSG to free up its sound resources.
    this.psg.dispose();
  }
  
  /**
   * Gets the Joystick of this Machine.
   * 
   * @return The Joystick of this Machine.
   */
  public Joystick getJoystick() {
    return joystick;
  }

  /**
   * Gets the Cpu6809 of this Machine.
   * 
   * @return The Cpu6809 of this Machine.
   */
  public Cpu6809 getCpu() {
    return cpu;
  }
  
  public void printState() {
    // TODO: Add more debug state.
    System.out.println(via.toString());
  }
}
