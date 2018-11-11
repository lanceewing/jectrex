package emu.jectrex.memory;

import com.badlogic.gdx.Gdx;

import emu.jectrex.cpu.Cpu6809;
import emu.jectrex.io.Via6522;

/**
 * This class emulates the Vectrex's memory.
 * 
 * @author Lance Ewing
 */
public class Memory {

  /**
   * Holds an array of references to instances of MemoryMappedChip where each
   * instance determines the behaviour of reading or writing to the given memory
   * address.
   */
  private MemoryMappedChip memoryMap[];
  
  /**
   * Constructor for Memory. Mainly available for unit testing.
   * 
   * @param cpu The CPU that will access this Memory.
   * @param allRam true if memory should be initialised to all RAM; otherwise false.
   */
  public Memory(Cpu6809 cpu, boolean allRam) {
    this.memoryMap = new MemoryMappedChip[65536];
    cpu.setMemory(this);
    if (allRam) {
      mapChipToMemory(new RamChip(0x10000), 0x0000, 0xFFFF);
    }
  }
  
  /**
   * Constructor for Memory.
   * 
   * @param cpu The CPU that will access this Memory.
   * @param via The memory mapped 6522 VIA chip.
   */
  public Memory(Cpu6809 cpu, Via6522 via) {
    this(cpu, false);
    initVectrexMemory(via);
  }
  
  /**
   * Initialises the memory map for Vectrex emulation.
   */
  public void initVectrexMemory(Via6522 via) {
    // 0000-7fff Cartridge ROM Space. Without a cartridge, it is unconnected.
    mapChipToMemory(new UnconnectedMemory(), 0x0000, 0x7FFF);

    // 8000-C7FF Unmapped space.
    mapChipToMemory(new UnconnectedMemory(), 0x8000, 0xC7FF);

    // C800-CFFF Vectrex RAM Space 1Kx8, shadowed twice. (r/w)
    RamChip ram = new RamChip(0x0400);
    mapChipToMemory(ram, 0xC800, 0xCFFF);
    
    // D000-D7FF 6522 VIA shadowed 128 times (r/w)
    mapChipToMemory(via, 0xD000, 0xD7FF);

    // D800-DFFF Don't use this area. Both the 6522 and RAM are selected in any reads/writes to this area.
    NotFullyDecodedMemory ramAndVia = new NotFullyDecodedMemory(new MemoryMappedChip[] { ram, via});
    mapChipToMemory(ramAndVia, 0xD800, 0xDFFF);

    // [A15 == 1 && A14 == 1 && A13 == 1] 
    // E000-FFFF System ROM Space 8Kx8 (r/w)
    // E000-EFFF is ROM, the built in game MINE STORM.
    // F000-FFFF Executive (power-up / reset handler and a large selection of subroutines for drawing, calculation, game logic and / or hardware maintenance)
    mapChipToMemory(new RomChip(convertByteArrayToIntArray(Gdx.files.internal("roms/system.bin").readBytes())), 0xE000, 0xFFFF);
  }
  
  /**
   * Converts a byte array into an int array.
   * 
   * @param data The byte array to convert.
   * 
   * @return The int array.
   */
  private int[] convertByteArrayToIntArray(byte[] data) {
    int[] convertedData = new int[data.length];
    for (int i=0; i<data.length; i++) {
      convertedData[i] = ((int)data[i]) & 0xFF;
    }
    return convertedData;
  }
  
  /**
   * Maps the given chip instance at the given add.lengthress range.
   * 
   * @param chip The chip to map at the given address range.
   * @param startAddress The start of the address range.
   * @param endAddress The end of the address range.
   */
  public void mapChipToMemory(MemoryMappedChip chip, int startAddress, int endAddress) {
    // Configure the chip into the memory map between the given start and end addresses.
    for (int i = startAddress; i <= endAddress; i++) {
      memoryMap[i] = chip;
    }

    chip.setMemory(this);
  }
  
  /**
   * Loads a ROM file from the given byte array at the given memory address.
   * 
   * @param romData The byte array containing the ROM program data to load.
   */
  public void loadCustomRom(int address, byte[] romData) {
    mapChipToMemory(new RomChip(convertByteArrayToIntArray(romData)), address, address + (romData.length - 1));
  }

  /**
   * Gets the array of memory mapped devices. 
   * 
   * @return The array of memory mapped devices.
   */
  public MemoryMappedChip[] getMemoryMap() {
    return memoryMap;
  }
  
  /**
   * Reads the value stored in the given memory address.
   * 
   * @param address The address to read the byte from.
   * 
   * @return The contents of the memory address.
   */
  public int readMemory(int address) {
    return (memoryMap[address].readMemory(address));
  }

  /**
   * Writes a value to the given memory address.
   * 
   * @param address The address to write the value to.
   * @param value The value to write to the given address.
   */
  public void writeMemory(int address, int value) {
    memoryMap[address].writeMemory(address, value);
  }
}
