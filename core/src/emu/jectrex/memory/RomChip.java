package emu.jectrex.memory;

/**
 * This class emulates a ROM chip.
 *
 * @author Lance Ewing
 */
public class RomChip extends MemoryMappedChip {

  private int size;
  private int[] mem;
  
  /**
   * Constructor for RomChip.
   * 
   * @param data The ROM chip data.
   */
  public RomChip(int[] data) {
    this.size = data.length;
    this.mem = data;
  }
  
  /**
   * Reads the value of the given memory address.
   *
   * @param address the address to read the byte from.
   *
   * @return the contents of the memory address.
   */
  public int readMemory(int address) {
    return mem[address % size];
  }

  /**
   * Writes a value to the given memory address.
   *
   * @param address the address to write the value to.
   * @param value the value to write to the given address.
   */
  public void writeMemory(int address, int value) {
    // Has no effect.
  }
}
