package emu.jectrex;

import emu.jectrex.memory.*;

/**
 * This is the base class of all chips.
 *
 * @author Lance Ewing
 */
public abstract class BaseChip {

  /**
   * Holds a reference to the machine's memory map.
   */
  protected Memory memory;

  /**
   * Holds an array of references to instances of MemoryMappedChip where each
   * instance determines the behaviour of reading or writing to the given memory
   * address.
   */
  protected MemoryMappedChip memoryMap[];
  
  /**
   * Sets a reference to the memory map. 
   *  
   * @param memory The memory map for the emulator.
   */
  public void setMemory(Memory memory) {
    this.memory = memory;
    this.memoryMap = memory.getMemoryMap();
  }
}
