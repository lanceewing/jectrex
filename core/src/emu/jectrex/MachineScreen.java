package emu.jectrex;

import com.badlogic.gdx.Application.ApplicationType;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.InputMultiplexer;
import com.badlogic.gdx.Screen;
import com.badlogic.gdx.graphics.Camera;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.OrthographicCamera;
import com.badlogic.gdx.graphics.Pixmap.Format;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.glutils.FrameBuffer;
import com.badlogic.gdx.graphics.glutils.ShaderProgram;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer.ShapeType;
import com.badlogic.gdx.math.Matrix4;
import com.badlogic.gdx.scenes.scene2d.Stage;
import com.badlogic.gdx.scenes.scene2d.ui.Skin;
import com.badlogic.gdx.scenes.scene2d.ui.Touchpad;
import com.badlogic.gdx.scenes.scene2d.ui.Touchpad.TouchpadStyle;
import com.badlogic.gdx.scenes.scene2d.utils.Drawable;
import com.badlogic.gdx.utils.TimeUtils;
import com.badlogic.gdx.utils.viewport.ExtendViewport;
import com.badlogic.gdx.utils.viewport.Viewport;

import emu.jectrex.config.AppConfigItem;
import emu.jectrex.ui.DialogHandler;
import emu.jectrex.ui.MachineInputProcessor;
import emu.jectrex.ui.ViewportManager;
import emu.jectrex.video.Video.Frame;
import emu.jectrex.video.Video.Phosphor;
import emu.jectrex.video.Video.Phosphors;

/**
 * The main screen in the Jectrex emulator, i.e. the one that shows the analog
 * vector output.
 * 
 * @author Lance Ewing
 */
public class MachineScreen implements Screen {

  // Screen dimensions.
  public static final int SCREEN_HEIGHT = 640;
  public static final int SCREEN_WIDTH = 512;
  public static final int SCREEN_HALF_HEIGHT = (SCREEN_HEIGHT / 2);
  public static final int SCREEN_HALF_WIDTH = (SCREEN_WIDTH / 2);
  
  /**
   * The Game object for Jectrex. Allows us to easily change screens.
   */
  private Jectrex jectrex;
  
  /**
   * This represents the Vectrex machine.
   */
  private Machine machine;

  /**
   * The Thread that updates the machine at the expected rate.
   */
  private MachineRunnable machineRunnable;
  
  /**
   * The InputProcessor for the MachineScreen. Handles the key and touch input.
   */
  private MachineInputProcessor machineInputProcessor;
  
  /**
   * This is an InputMultiplexor, which includes both the Stage and the MachineScreen.
   */
  private InputMultiplexer portraitInputProcessor;
  private InputMultiplexer landscapeInputProcessor;
  
  /**
   * SpriteBatch shared by all rendered components.
   */
  private SpriteBatch batch;
  
  /**
   * ShapeRenderer to draw the phosphors with.
   */
  private ShapeRenderer shapeRenderer;
  
  // A projection matrix appropriate to use with the various post processing frame buffers.
  private Matrix4 fboProjection;
  
  // Currently in use components to support rendering of the Vectrex screen. The objects 
  // that these references point to will change depending on the MachineType.
  private Viewport viewport;
  private Camera camera;
  
  // UI components.
  private Texture joystickIcon;
  private Texture keyboardIcon;
  private Texture backIcon;
  private Texture warpSpeedIcon;
  private Texture cameraIcon;

  private ViewportManager viewportManager;
  
  // Touchpad
  private Stage portraitStage;
  private Stage landscapeStage;
  private Touchpad portraitTouchpad;
  private Touchpad landscapeTouchpad;
  
  // FPS text font
  private BitmapFont font;
  
  private boolean showFPS = true;   // TODO: Remove setting to true. Temporary testing.
  
  /**
   * Details about the application currently running.
   */
  private AppConfigItem appConfigItem;
  
  /**
   * Constructor for MachineScreen.
   * 
   * @param jectrex The Jectrex instance.
   * @param dialogHandler
   */
  public MachineScreen(Jectrex jectrex, DialogHandler dialogHandler) {
    this.jectrex = jectrex;
    this.machine = new Machine();
    this.machineRunnable = new MachineRunnable(this.machine);
    
    batch = new SpriteBatch();
    shapeRenderer = new ShapeRenderer(50000);
    
    // Create a projection matrix that flips the Y axis, and centres based on screen dimensions.
    fboProjection = (new Matrix4())
        .setToOrtho2D(-SCREEN_HALF_WIDTH, -SCREEN_HALF_HEIGHT, SCREEN_WIDTH, SCREEN_HEIGHT)
        .mul((new Matrix4()).setToScaling(1.0f, -1.0f, 1.0f));
    
    initGLComponents();
    
    camera = new OrthographicCamera();
    viewport = new ExtendViewport(SCREEN_WIDTH, SCREEN_HEIGHT, camera);
    
    createColors();
    
    keyboardIcon = new Texture("png/keyboard_icon.png");
    joystickIcon = new Texture("png/joystick_icon.png");
    backIcon = new Texture("png/back_arrow.png");
    warpSpeedIcon = new Texture("png/warp_speed_icon.png");
    cameraIcon = new Texture("png/camera_icon.png");
    
    // Create the portrait and landscape joystick touchpads.
    portraitTouchpad = createTouchpad(300);
    landscapeTouchpad = createTouchpad(200);
    
    viewportManager = ViewportManager.getInstance();
    
    //Create a Stage and add TouchPad
    portraitStage = new Stage(viewportManager.getPortraitViewport(), batch);
    portraitStage.addActor(portraitTouchpad);
    landscapeStage = new Stage(viewportManager.getLandscapeViewport(), batch);
    landscapeStage.addActor(landscapeTouchpad);
    
    // Create and register an input processor for keys, etc.
    machineInputProcessor = new MachineInputProcessor(this, dialogHandler);
    portraitInputProcessor = new InputMultiplexer();
    portraitInputProcessor.addProcessor(portraitStage);
    portraitInputProcessor.addProcessor(machineInputProcessor);
    landscapeInputProcessor = new InputMultiplexer();
    landscapeInputProcessor.addProcessor(landscapeStage);
    landscapeInputProcessor.addProcessor(machineInputProcessor);
    
    // FPS font
    font = new BitmapFont(Gdx.files.internal("data/default.fnt"), false);
    font.setFixedWidthGlyphs(".  *");  // Note: The * and . are ignored, first and last. Only the space is fixed width.
    font.setColor(new Color(0x808080FF));
    font.getData().setScale(2f, 2f);
    
    // Start up the MachineRunnable Thread. It will initially be paused, awaiting machine configuration.
    Thread machineThread = new Thread(this.machineRunnable);
    machineThread.start();
  }
  
  protected Touchpad createTouchpad(int size) {
    Skin touchpadSkin = new Skin();
    touchpadSkin.add("touchBackground", new Texture("png/touchBackground.png"));
    touchpadSkin.add("touchKnob", new Texture("png/touchKnob.png"));
    TouchpadStyle touchpadStyle = new TouchpadStyle();
    Drawable touchBackground = touchpadSkin.getDrawable("touchBackground");
    Drawable touchKnob = touchpadSkin.getDrawable("touchKnob");
    touchpadStyle.background = touchBackground;
    touchpadStyle.knob = touchKnob;
    Touchpad touchpad = new Touchpad(10, touchpadStyle);
    touchpad.setBounds(15, 15, size, size);
    return touchpad;
  }
  
  private Color[] colors;
  private float[] circleSizes;
  
  private void createColors() {
    colors = new Color[128];
    circleSizes = new float[128];
    for (int z = 0; z < 128; z++) {
      float zz = ((float)z / 256.0f) + 0.5f;
      colors[z] = new Color(zz, zz, zz, zz);
      circleSizes[z] = 3 * (1.0f - zz);
    }
  }
  
  /**
   * Initialises the Machine with the given AppConfigItem. This will represent an app that was
   * selected on the HomeScreen. As part of this initialisation, it creates the Pixmap, screen
   * Textures, Camera and Viewport required to render the Vectrex screen at the size needed for
   * the MachineType being emulated.
   * 
   * @param appConfigItem The configuration for the app that was selected on the HomeScreen.
   */
  public void initMachine(AppConfigItem appConfigItem) {
    this.appConfigItem = appConfigItem;
    
    if ((appConfigItem.getFileType() == null) || appConfigItem.getFileType().equals("")) {
      // If there is no file type, there is no file to load and we simply boot into the built in ROM, i.e. Mine Storm.
      machine.init();
    } else {
      // Otherwise there is a file to load.
      machine.init(appConfigItem.getFilePath(), appConfigItem.getFileType(), appConfigItem.getFileLocation());
    }
  }
  
  private long lastLogTime;
  private long avgRenderTime;
  private long avgDrawTime;
  private long renderCount;
  
  private int addPosition;
  private int fadePosition;
  private Phosphor[] dots;
  
  @Override
  public void render(float delta) {
    long renderStartTime = TimeUtils.nanoTime();
    long fps = Gdx.graphics.getFramesPerSecond();
    long maxFrameDuration = (long)(1000000000L * (fps == 0? 0.016667f : delta));
    boolean draw = false;
    Phosphors phosphors = null;
    
    if (machine.isPaused()) {
      // When paused, we limit the draw frequency since there isn't anything to change.
      draw = ((fps < 30) || ((renderCount % (fps/30)) == 0));
      
    } else {
      Frame frame = machine.getFrame();
      if (frame != null) {
        phosphors = frame.phosphors;
      }
      draw = true;
    }
    
    if (draw) {
      draw(delta, phosphors);
      long drawDuration = TimeUtils.nanoTime() - renderStartTime;
      if (renderCount == 0) {
        avgDrawTime = drawDuration;
      } else {
        avgDrawTime = ((avgDrawTime * renderCount) + drawDuration) / (renderCount + 1);
      }
    }
    
    long renderDuration = TimeUtils.nanoTime() - renderStartTime;
    if (renderCount == 0) {
      avgRenderTime = renderDuration;
    } else {
      avgRenderTime = ((avgRenderTime * renderCount) + renderDuration) / (renderCount + 1);
    }
    
    renderCount++;
    
    if ((lastLogTime == 0) || (renderStartTime - lastLogTime > 10000000000L)) {
      lastLogTime = renderStartTime;
    }
  }
  
  private void draw(float delta, Phosphors phosphors) {
    // Get the KeyboardType currently being used by the MachineScreenProcessor.
    KeyboardType keyboardType = machineInputProcessor.getKeyboardType();
    
    // Make our offscreen FBO the current buffer.
    fbo1.begin();

    Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT);
    Gdx.gl.glDisable(GL20.GL_DEPTH_TEST);
    Gdx.gl.glEnable(GL20.GL_BLEND);
    Gdx.gl.glBlendFunc(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA);
    
    // Render the Vectrex screen.
    camera.update();
    
    shapeRenderer.setProjectionMatrix(fboProjection);
    shapeRenderer.begin(ShapeType.Line);
    
    Color c = Color.WHITE;
    
    if (phosphors != null) {
      addPosition = phosphors.getAddPosition();
      fadePosition = phosphors.getFadePosition();
      dots = phosphors.getDots();
    }
    
    int maxDots = Phosphors.NUM_OF_PHOSPHORS;
    boolean foundNewFadePosition = false;
    Phosphor lastDot = null;
    
    for (int i=fadePosition; i != addPosition; i = ((i + 1) % maxDots)) {
      Phosphor dot = dots[i];
      
      if (dot.z > 0) {
        Color c1 = colors[dot.z];
        shapeRenderer.setColor(c1);
        
        if (dot.start || (lastDot== null)) {
          shapeRenderer.point(dot.x, dot.y, 0);
        }
        else {
          shapeRenderer.circle(dot.x-1, dot.y-1, 0.5f);
        }
        
        // Reduce Z for this dot by 64, which is the number of Z levels faded for a single frame.
        dot.z -= 32;
        
        if (dot.z <= 0) {
          // If this dot's Z is below zero, and we haven't yet found a new fade position, we 
          // adjust the fade position to the dot after this one.
          
          if (!foundNewFadePosition && (phosphors != null)) {
            fadePosition = i + 1;
          }
          
        } else {
          // When  we find the first Z position still above 0, we stop adjusting the fade position.
          foundNewFadePosition = true;
        }
      }
      
      lastDot = dot;
    }
      
    if (phosphors != null) {
      phosphors.setFadePosition(fadePosition % maxDots);
    }
    
    shapeRenderer.end();

    // Unbind the FBO
    fbo1.end();
    
    Texture gameTexture = fbo1.getColorBufferTexture();
    
    // Render the game texture with persistence from previous frames.
    fbo2.begin();
    Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT);
    Gdx.gl.glDisable(GL20.GL_DEPTH_TEST);
    batch.setProjectionMatrix(fboProjection);
    batch.begin();
    batch.setShader(persistenceShader);
    persistenceFbo.getColorBufferTexture().bind(1);
    persistenceShader.setUniformi("r", 1);
    fbo1.getColorBufferTexture().bind(0);
    batch.draw(gameTexture, -SCREEN_HALF_WIDTH, -SCREEN_HALF_HEIGHT);
    batch.end();
    batch.setShader(null);
    fbo2.end();
    
    persistenceFbo.begin();
    Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT);
    Gdx.gl.glDisable(GL20.GL_DEPTH_TEST);
    Gdx.gl.glEnable(GL20.GL_BLEND);
    Gdx.gl.glBlendFunc(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA);
    batch.setProjectionMatrix(fboProjection);
    batch.disableBlending();
    batch.setShader(copyShader);
    batch.begin();
    batch.draw(fbo2.getColorBufferTexture(), -SCREEN_HALF_WIDTH, -SCREEN_HALF_HEIGHT);
    batch.end();
    batch.setShader(null);
    persistenceFbo.end();
    
    Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT);
    Gdx.gl.glDisable(GL20.GL_DEPTH_TEST);
    
    batch.setProjectionMatrix(camera.combined);
    batch.begin();
    batch.draw(fbo2.getColorBufferTexture(), 0, 0);
    batch.end();
    
    // Render the UI elements, e.g. the keyboard and joystick icons.
    viewportManager.getCurrentCamera().update();
    batch.setProjectionMatrix(viewportManager.getCurrentCamera().combined);
    batch.enableBlending();
    batch.begin();
    if (keyboardType.equals(KeyboardType.JOYSTICK)) {
      if (viewportManager.isPortrait()) {
        //batch.draw(keyboardType.getTexture(KeyboardType.LEFT), 0, 0);
        batch.draw(keyboardType.getTexture(KeyboardType.RIGHT), viewportManager.getWidth() - 135, 0);
      } else {
        //batch.draw(keyboardType.getTexture(KeyboardType.LEFT), 0, 0, 201, 201);
        batch.draw(keyboardType.getTexture(KeyboardType.RIGHT), viewportManager.getWidth() - 135, 0);
      }
    } else 
    if (keyboardType.isRendered()) {
      batch.setColor(c.r, c.g, c.b, keyboardType.getOpacity());
      batch.draw(keyboardType.getTexture(), 0, keyboardType.getRenderOffset());
    }
    else if (keyboardType.equals(KeyboardType.OFF)) {
      // The keyboard and joystick icons are rendered only when an input type isn't showing.
      batch.setColor(c.r, c.g, c.b, 0.5f);
      if (viewportManager.isPortrait()) {
        batch.draw(joystickIcon, 0, 0);
        if (Gdx.app.getType().equals(ApplicationType.Android)) {
          // Main Vectrex keyboard on the right.
          batch.draw(keyboardIcon, viewportManager.getWidth() - 145, 0);
          // Mobile keyboard for debug purpose. Wouldn't normally make this available.
          batch.setColor(c.r, c.g, c.b, 0.15f);
          batch.draw(keyboardIcon, viewportManager.getWidth() - viewportManager.getWidth()/2 - 70, 0);
          
        } else {
          // Desktop puts Vectrex keyboard button in the middle.
          batch.draw(keyboardIcon, viewportManager.getWidth() - viewportManager.getWidth()/2 - 70, 0);
          // and the back button on the right.
          batch.draw(backIcon, viewportManager.getWidth() - 145, 0);
        }
        
      } else {
        batch.draw(joystickIcon, 0, viewportManager.getHeight() - 140);
        batch.draw(keyboardIcon, viewportManager.getWidth() - 150, viewportManager.getHeight() - 125);
        batch.draw(backIcon, viewportManager.getWidth() - 150, 0);
      }
    }
    float vectrexScreenHeight = 550;
    if (machineRunnable.isWarpSpeed()) {
      batch.setColor(0.5f, 1.0f, 0.5f, 1.0f);
    } else {
      batch.setColor(c.r, c.g, c.b, 0.5f);
    }
    batch.draw(warpSpeedIcon, 0, vectrexScreenHeight - 128);
    batch.setColor(c.r, c.g, c.b, 0.5f);
    batch.draw(cameraIcon, viewportManager.getWidth() - 145, vectrexScreenHeight - 128);
    if (showFPS) {
      font.draw(batch, String.format("%4dFPS", machineRunnable.getFramesLastSecond()), 
          viewportManager.getWidth() - viewportManager.getWidth()/2 - 90, vectrexScreenHeight - 48);
    }
    batch.end();
    if (keyboardType.equals(KeyboardType.JOYSTICK)) {
      float joyX = 0;
      float joyY = 0;
      if (viewportManager.isPortrait()) {
        portraitStage.act(delta);
        portraitStage.draw();
        joyX = portraitTouchpad.getKnobPercentX();
        joyY = portraitTouchpad.getKnobPercentY();
      } else {
        landscapeStage.act(delta);
        landscapeStage.draw();
        joyX = landscapeTouchpad.getKnobPercentX();
        joyY = landscapeTouchpad.getKnobPercentY();
      }
      machine.getJoystick().touchPad(joyX, joyY);
    }
  }
  
  /**
   * Toggles the display of the FPS.
   */
  public void toggleShowFPS() {
    showFPS = !showFPS;
  }
  
  @Override
  public void resize(int width, int height) {
    viewport.update(width, height, false);
    
    // Align Vectrex screen's top edge to top of the viewport.
    Camera camera = viewport.getCamera();
    camera.position.x = machine.getScreenWidth() /2;
    camera.position.y = machine.getScreenHeight() - viewport.getWorldHeight()/2;
    camera.update();
    
    machineInputProcessor.resize(width, height);
    viewportManager.update(width, height);
    
    if (viewportManager.isPortrait()) {
      Gdx.input.setInputProcessor(portraitInputProcessor);
    } else {
      Gdx.input.setInputProcessor(landscapeInputProcessor);
    }
  }

  @Override
  public void pause() {
    // On Android, this is also called when the "Home" button is pressed.
    machineRunnable.pause();
  }

  @Override
  public void resume() {
    KeyboardType.init();
    machineRunnable.resume();
  }
  
  @Override
  public void show() {
    // Note that this screen should not be shown unless the Machine has been initialised by calling
    // the initMachine method of MachineScreen. This will create the necessary PixMap and Textures 
    // required for the MachineType.
    KeyboardType.init();
    if (viewportManager.isPortrait()) {
      Gdx.input.setInputProcessor(portraitInputProcessor);
    } else {
      Gdx.input.setInputProcessor(landscapeInputProcessor);
    }
    machineRunnable.resume();
    Gdx.gl.glClearColor(0, 0, 0, 1);
  }
  
  @Override
  public void hide() {
    // On Android, this is also called when the "Back" button is pressed.
    KeyboardType.dispose();
  }

  @Override
  public void dispose() {
    KeyboardType.dispose();
    keyboardIcon.dispose();
    joystickIcon.dispose();
    batch.dispose();
    machineRunnable.stop();
    disposeGLComponents();
  }
  
  /**
   * Gets the Machine that this MachineScreen is running.
   *  
   * @return The Machine that this MachineScreen is running.
   */
  public Machine getMachine() {
    return machine;
  }
  
  /**
   * Gets the MachineRunnable that is running the Machine.
   * 
   * @return The MachineRunnable that is running the Machine.
   */
  public MachineRunnable getMachineRunnable() {
    return machineRunnable;
  }
  
  /**
   * Returns user to the Home screen.
   */
  public void exit() {
    jectrex.setScreen(jectrex.getHomeScreen());
  }

  private ShaderProgram copyShader;
  private ShaderProgram persistenceShader;

  private FrameBuffer persistenceFbo;
  private FrameBuffer fbo1;
  private FrameBuffer fbo2;
  
  public void initGLComponents() {
    String staticVert = Gdx.files.internal("glsl/static.glsl").readString();
    //String staticVert = Gdx.files.internal("glsl/vertex.glsl").readString();
    String copyFrag = Gdx.files.internal("glsl/copy.glsl").readString();
    String persistenceFrag = Gdx.files.internal("glsl/persistence.glsl").readString();
    copyShader = new ShaderProgram(staticVert, copyFrag);
    persistenceShader = new ShaderProgram(staticVert, persistenceFrag);
    persistenceFbo = FrameBuffer.createFrameBuffer(Format.RGBA8888, SCREEN_WIDTH, SCREEN_HEIGHT, false);
    fbo1 = FrameBuffer.createFrameBuffer(Format.RGBA8888, SCREEN_WIDTH, SCREEN_HEIGHT, false);
    fbo2 = FrameBuffer.createFrameBuffer(Format.RGBA8888, SCREEN_WIDTH, SCREEN_HEIGHT, false);
  }
  
  public void disposeGLComponents() {
    copyShader.dispose();
    persistenceShader.dispose();
    persistenceFbo.dispose();
    fbo1.dispose();
    fbo2.dispose();
  }
}
