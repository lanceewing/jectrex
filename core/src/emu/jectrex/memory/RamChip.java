package emu.jectrex.memory;

/**
 * This class emulates an 8-bit RAM chip.
 *
 * @author Lance Ewing
 */
public class RamChip extends MemoryMappedChip {

  private int size;
  private int[] mem;
  
  /**
   * Constructor for RamChip.
   * 
   * @param size
   */
  public RamChip(int size) {
    this.size = size;
    this.mem = new int[size];
    
    for (int i = 0; i < size; i++) {
      this.mem[i] = ((i & 128) != 0 ? 0xFF : 0);
    }
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
    mem[address % size] = (value & 0xFF);
  }
}
