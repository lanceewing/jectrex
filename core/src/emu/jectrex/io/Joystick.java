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

  private HashMap<JoystickPosition, Boolean> state;
  
  /**
   * Constructor for Joystick.
   */
  public Joystick() {
    this.state = new HashMap<JoystickPosition, Boolean>();
    this.state.put(JoystickPosition.LEFT, false);
    this.state.put(JoystickPosition.RIGHT, false);
    this.state.put(JoystickPosition.UP, false);
    this.state.put(JoystickPosition.DOWN, false);
  }
  
  /**
   * Invoked when a key has been pressed.
   *
   * @param keycode The keycode of the key that has been pressed.
   */
  public void keyPressed(int keycode) {

  }

  /**
   * Invoked when a key has been released.
   *
   * @param keycode The keycode of the key that has been released.
   */
  public void keyReleased(int keycode) {

  }
  
  /**
   * Checks for significant changes in the joystick position.
   * 
   * @param x
   * @param y
   */
  public void touchPad(float x, float y) {
    if (x > 0.3) {
      // Right
      if (state.get(JoystickPosition.LEFT)) {
        exitLeft();
      }
      if (!state.get(JoystickPosition.RIGHT)) {
        enterRight();
      }
    } else if (x < -0.3) {
      // Left
      if (state.get(JoystickPosition.RIGHT)) {
        exitRight();
      }
      if (!state.get(JoystickPosition.LEFT)) {
        enterLeft();
      }
    } else {
      // Not left or right at the moment.
      if (state.get(JoystickPosition.RIGHT)) {
        exitRight();
      } else if (state.get(JoystickPosition.LEFT)) {
        exitLeft();
      }
    }
    
    if (y > 0.3) {
      // Up
      if (state.get(JoystickPosition.DOWN)) {
        exitDown();
      }
      if (!state.get(JoystickPosition.UP)) {
        enterUp();
      }
    } else if (y < -0.3) {
      // Down
      if (state.get(JoystickPosition.UP)) {
        exitUp();
      }
      if (!state.get(JoystickPosition.DOWN)) {
        enterDown();
      }
    } else {
      // Not left or right at the moment.
      if (state.get(JoystickPosition.UP)) {
        exitUp();
      } else if (state.get(JoystickPosition.DOWN)) {
        exitDown();
      }
    }
  }

  private void enterDown() {
    state.put(JoystickPosition.DOWN, true);

  }
  
  private void exitDown() {
    state.put(JoystickPosition.DOWN, false);

  }
  
  private void enterUp() {
    state.put(JoystickPosition.UP, true);

  }
  
  private void exitUp() {
    state.put(JoystickPosition.UP, false);

  }

  private void enterLeft() {
    state.put(JoystickPosition.LEFT, true);

  }
  
  private void exitLeft() {
    state.put(JoystickPosition.LEFT, false);

  }

  private void enterRight() {
    state.put(JoystickPosition.RIGHT, true);

  }
  
  private void exitRight() {
    state.put(JoystickPosition.RIGHT, false);

  }
}
