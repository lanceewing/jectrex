package emu.jectrex;

import com.badlogic.gdx.Game;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Preferences;

import emu.jectrex.config.AppConfigItem;
import emu.jectrex.config.AppConfigItem.FileLocation;
import emu.jectrex.ui.DialogHandler;

/**
 * The main entry point in to the cross-platform part of the Jectrex emulator. A multi-screen
 * libGDX application needs to extend the Game class, which is what we do here. It allows 
 * us to have other screens, such as various menu screens.
 * 
 * @author Lance Ewing
 */
public class Jectrex extends Game {
  
  /**
   * This is the screen that is used to show the running emulation.
   */
  private MachineScreen machineScreen;
  
  /**
   * This is the screen that shows the boot options and programs to load.
   */
  private HomeScreen homeScreen;
  
  /**
   * Invoked by Jectrex whenever it would like to show a dialog, such as when it needs
   * the user to confirm an action, or to choose a file.
   */
  private DialogHandler dialogHandler;
  
  /**
   * Command line args. Mainly applicable to desktop.
   */
  private String[] args;
  
  /**
   * Jectrex's saved preferences.
   */
  private Preferences preferences;
  
  /**
   * Jectrex's application screenshot storage.
   */
  private Preferences screenshotStore;
  
  /**
   * Constructor for Jectrex.
   * 
   * @param dialogHandler
   * @param args Command line args.
   */
  public Jectrex(DialogHandler dialogHandler, String... args) {
    this.dialogHandler = dialogHandler;
    this.args = args;
  }
  
  @Override
  public void create () {
    preferences = Gdx.app.getPreferences("jectrex.preferences");
    screenshotStore = Gdx.app.getPreferences("jectrex_screens.store");
    machineScreen = new MachineScreen(this, dialogHandler);
    homeScreen = new HomeScreen(this, dialogHandler);

    if ((args != null) && (args.length > 0)) {
      AppConfigItem appConfigItem = new AppConfigItem();
      appConfigItem.setFilePath(args[0]);
      appConfigItem.setFileType("CART");
      appConfigItem.setFileLocation(FileLocation.ABSOLUTE);
      MachineScreen machineScreen = getMachineScreen();
      machineScreen.initMachine(appConfigItem);
      setScreen(machineScreen);
    } else {
      setScreen(homeScreen);
    }
    
    // Stop the back key from immediately exiting the app.
    Gdx.input.setCatchBackKey(true);
  }
  
  /**
   * Gets the MachineScreen.
   * 
   * @return The MachineScreen.
   */
  public MachineScreen getMachineScreen() {
    return machineScreen;
  }
  
  /**
   * Gets the HomeScreen.
   * 
   * @return the HomeScreen.
   */
  public HomeScreen getHomeScreen() {
    return homeScreen;
  }
  
  /**
   * Gets the Preferences for Jectrex.
   * 
   * @return The Preferences for Jectrex.
   */
  public Preferences getPreferences() {
    return preferences;
  }
  
  /**
   * Gets the screenshot store for Jectrex. 
   * 
   * @return The screenshot store for Jectrex.
   */
  public Preferences getScreenshotStore() {
    return screenshotStore;
  }
  
  @Override
  public void dispose () {
    super.dispose();
    
    // For now we'll dispose the MachineScreen here. As the emulator grows and
    // adds more screens, this may be managed in a different way. Note that the
    // super dispose does not call dispose on the screen.
    machineScreen.dispose();
    homeScreen.dispose();
    
    // Save the preferences when the emulator is closed.
    preferences.flush();
    screenshotStore.flush();
  }
}