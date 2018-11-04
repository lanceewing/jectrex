package emu.jectrex.memory;

/**
 * This class emulates a 4-bit RAM chip (nibble).
 *
 * @author Lance Ewing
 */
public class NibbleRamChip extends MemoryMappedChip {

  private int size;
  private int[] mem;
  
  /**
   * Constructor for NibbleRamChip.
   * 
   * @param size The size of the RAM chip in nibbles.
   */
  public NibbleRamChip(int size) {
    this.size = size;
    this.mem = new int[size];
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
    mem[address % size] = (value & 0x0F);
  }
}
