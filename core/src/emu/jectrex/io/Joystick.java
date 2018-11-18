package emu.jectrex.io;

import java.util.HashMap;

import com.badlogic.gdx.Input.Keys;

/**
 * This class emulates the Vectrex controller.
 * 
 * @author Lance Ewing
 */
public class Joystick {
  
  public static enum JoystickPosition {
    LEFT, RIGHT, UP, DOWN;
  }
  
  private HashMap<JoystickPosition, Boolean> directionState;
  
  /**
   * Data used to convert Java keypresses into Joystick button signals.
   */
  private static int keyToButtonData[][] = {
    {Keys.A, 0x01},
    {Keys.S, 0x02},
    {Keys.D, 0x04},
    {Keys.F, 0x08}
  };
  
  /**
   * Data used to convert Java keypresses into Joystick direction codes.
   */
  private static int keyToDirectionData[][] = {
    {Keys.LEFT,  JoystickPosition.LEFT.ordinal()},
    {Keys.RIGHT, JoystickPosition.RIGHT.ordinal()},
    {Keys.UP,    JoystickPosition.UP.ordinal()},
    {Keys.DOWN,  JoystickPosition.DOWN.ordinal()}
  };
  
  /**
   * HashMap used to store mappings between Java key events and joystick button signals.
   */
  private HashMap<Integer, Integer> keyToButtonMap;
  
  /**
   * HashMap used to store mappings between Java key events and joystick directions.
   */
  private HashMap<Integer, JoystickPosition> keyToDirectionMap;
  
  /**
   * The current state of the joystick buttons.
   */
  private int buttonState;
  
  /**
   * The current Joystick X direction, from 0 to 255.
   */
  private int xDirection;
  
  /**
   * The current Joystick Y direction, from 0 to 255.
   */
  private int yDirection;
  
  /**
   * The joystick direction COMPARE signal, which indicates whether the selected POT 
   * value has matched the value that the CPU is testing for via the DAC out.
   */
  private boolean compare;
  
  /**
   * Constructor for Joystick.
   */
  public Joystick() {
    this.directionState = new HashMap<JoystickPosition, Boolean>();
    this.directionState.put(JoystickPosition.LEFT, false);
    this.directionState.put(JoystickPosition.RIGHT, false);
    this.directionState.put(JoystickPosition.UP, false);
    this.directionState.put(JoystickPosition.DOWN, false);
    
    buttonState = 0xFF;
    
    keyToButtonMap = new HashMap<Integer, Integer>();
    
    // Initialise the key to joystick button signal HashMap.
    for (int i=0; i<keyToButtonData.length; i++) {
      keyToButtonMap.put(new Integer(keyToButtonData[i][0]), keyToButtonData[i][1]);
    }
    
    keyToDirectionMap = new HashMap<Integer, JoystickPosition>();
    
    // Initialise the key to joystick direction HashMap.
    for (int i=0; i<keyToDirectionData.length; i++) {
      keyToDirectionMap.put(new Integer(keyToDirectionData[i][0]), JoystickPosition.values()[keyToDirectionData[i][1]]);
    }
  }
  
  /**
   * Invoked when a key has been pressed.
   *
   * @param keycode The keycode of the key that has been pressed.
   */
  public void keyPressed(int keycode) {
    Integer joystickSignal = keyToButtonMap.get(keycode);
    if (joystickSignal != null) {
      buttonState &= (~joystickSignal);
    } else {
      JoystickPosition position = keyToDirectionMap.get(keycode);
      if (position != null) {
        directionState.put(position, true);
        switch (position) {
          case DOWN:
            yDirection = 0;
            break;
          case LEFT:
            xDirection = 0;
            break;
          case RIGHT:
            xDirection = 255;
            break;
          case UP:
            yDirection = 255;
            break;
          default:
            break;
        }
      }
    }
  }

  /**
   * Invoked when a key has been released.
   *
   * @param keycode The keycode of the key that has been released.
   */
  public void keyReleased(int keycode) {
    Integer joystickSignal = keyToButtonMap.get(keycode);
    if (joystickSignal != null) {
      buttonState |= joystickSignal;
    } else {
      JoystickPosition position = keyToDirectionMap.get(keycode);
      if (position != null) {
        directionState.put(position, false);
        switch (position) {
          case DOWN:
            yDirection = 128;
            break;
          case LEFT:
            xDirection = 128;
            break;
          case RIGHT:
            xDirection = 128;
            break;
          case UP:
            yDirection = 128;
            break;
          default:
            break;
        }
      }
    }
  }
  
  /**
   * Gets the current joystick button state.
   * 
   * @return The current joystick button state.
   */
  public int getButtonState() {
    return (buttonState & 0xFF);
  }
  
  /**
   * Returns the joystick direction COMPARE signal, which indicates whether the selected POT 
   * value has matched the value that the CPU is testing for via the DAC.
   *  
   * @return true if the selected POT value matches the DAC out; otherwise false.
   */
  public boolean getCompare() {
    return compare;
  }
  
  /**
   * 
   * 
   * @param muxChannelSelect
   * @param dacOut
   */
  public void processMux(int muxChannelSelect, int dacOut) {
    int directionValue = 128;
    switch (muxChannelSelect) {
      case 0: // Joystick 1 - Left and Right.
        directionValue = xDirection;
        break;
        
      case 1: // Joystick 1 - Up and Down.
        directionValue = yDirection;
        break;
        
      case 2: // Joystick 2 - Left and Right. 
        directionValue = 128;
        break;
        
      case 3: // Joystick 2 - Up and Down.
        directionValue = 128;
        break;
        
      default: // Ignore.
        break;
    }
    
    if (directionValue > dacOut) {
      compare = true;
    } else {
      compare = false;
    }
  }
  
  /**
   * Checks for significant changes in the joystick position.
   * 
   * @param x
   * @param y
   */
  public void touchPad(float x, float y) {
    // Returns the x-position of the knob as a percentage from the center of the touchpad to the edge of the circular movement
    // area. The positive direction is right. 
    this.xDirection = (int)((x + 1) * 255);    // RIGHT is 255, LEFT is 0.
    
    // Returns the y-position of the knob as a percentage from the center of the touchpad to the edge of the circular movement
    // area. The positive direction is up.
    this.yDirection = (int)((y + 1) * 255);    // UP is 255, DOWN is 0.
    
    if (x > 0.3) {
      // Right
      if (directionState.get(JoystickPosition.LEFT)) {
        exitLeft();
      }
      if (!directionState.get(JoystickPosition.RIGHT)) {
        enterRight();
      }
    } else if (x < -0.3) {
      // Left
      if (directionState.get(JoystickPosition.RIGHT)) {
        exitRight();
      }
      if (!directionState.get(JoystickPosition.LEFT)) {
        enterLeft();
      }
    } else {
      // Not left or right at the moment.
      if (directionState.get(JoystickPosition.RIGHT)) {
        exitRight();
      } else if (directionState.get(JoystickPosition.LEFT)) {
        exitLeft();
      }
    }
    
    if (y > 0.3) {
      // Up
      if (directionState.get(JoystickPosition.DOWN)) {
        exitDown();
      }
      if (!directionState.get(JoystickPosition.UP)) {
        enterUp();
      }
    } else if (y < -0.3) {
      // Down
      if (directionState.get(JoystickPosition.UP)) {
        exitUp();
      }
      if (!directionState.get(JoystickPosition.DOWN)) {
        enterDown();
      }
    } else {
      // Not left or right at the moment.
      if (directionState.get(JoystickPosition.UP)) {
        exitUp();
      } else if (directionState.get(JoystickPosition.DOWN)) {
        exitDown();
      }
    }
  }

  private void enterDown() {
    directionState.put(JoystickPosition.DOWN, true);

  }
  
  private void exitDown() {
    directionState.put(JoystickPosition.DOWN, false);

  }
  
  private void enterUp() {
    directionState.put(JoystickPosition.UP, true);

  }
  
  private void exitUp() {
    directionState.put(JoystickPosition.UP, false);

  }

  private void enterLeft() {
    directionState.put(JoystickPosition.LEFT, true);

  }
  
  private void exitLeft() {
    directionState.put(JoystickPosition.LEFT, false);

  }

  private void enterRight() {
    directionState.put(JoystickPosition.RIGHT, true);

  }
  
  private void exitRight() {
    directionState.put(JoystickPosition.RIGHT, false);

  }
}
