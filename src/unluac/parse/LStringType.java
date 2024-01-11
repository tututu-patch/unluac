package unluac.parse;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.ByteBuffer;

import unluac.Version;


public abstract class LStringType extends BObjectType<LString> {

  public static LStringType get(Version.StringType type) {
    switch(type) {
      case LUA50: return new LStringType50();
      case LUA53: return new LStringType53();
      case LUA54: return new LStringType54();
      default: throw new IllegalStateException();
    }
  }
  
  protected ThreadLocal<StringBuilder> b = new ThreadLocal<StringBuilder>() {
    
    @Override
    protected StringBuilder initialValue() {
      return new StringBuilder();  
    }

  };
  
}

class LStringType50 extends LStringType {
  
  @Override
  public LString parse(final ByteBuffer buffer, BHeader header) {
    BInteger sizeT = header.sizeT.parse(buffer, header);
    final StringBuilder b = this.b.get();
    b.setLength(0);
    sizeT.iterate(new Runnable() {
      
      @Override
      public void run() {
        b.append((char) (0xFF & buffer.get()));
      }
      
    });
    if(b.length() == 0) {
      return LString.NULL;
    } else {
      char last = b.charAt(b.length() - 1);
      b.delete(b.length() - 1, b.length());
      String s = b.toString();
      if(header.debug) {
        System.out.println("-- parsed <string> \"" + s + "\"");
      }
      return new LString(s, last);
    }
  }
  
  @Override
  public void write(OutputStream out, BHeader header, LString string) throws IOException {
    int len = string.value.length();
    if(string == LString.NULL) {
      header.sizeT.write(out, header, header.sizeT.create(0));
    } else {
      header.sizeT.write(out, header, header.sizeT.create(len + 1));
      for(int i = 0; i < len; i++) {
        out.write(string.value.charAt(i));
      }
      out.write(0);
    }
  }
}

class LStringType53 extends LStringType {
  
  @Override
  public LString parse(final ByteBuffer buffer, BHeader header) {
    BInteger sizeT;
    int size = 0xFF & buffer.get();
    if(size == 0) {
      return LString.NULL;
    } else if(size == 0xFF) {
      sizeT = header.sizeT.parse(buffer, header);
      size = sizeT.getValue(); // Assuming BInteger has a method to get its value as int
    } else {
      sizeT = new BInteger(size);
    }
    
    final StringBuilder b = this.b.get();
    b.setLength(0);
  
    // Read the entire string into the StringBuilder first
    for (int i = 0; i < size; i++) {
      b.append((char) (0xFF & buffer.get()));
    }
  
    // Define a new run() method for the new algorithm
    final int x = size - 1;
    Runnable run = new Runnable() {
      
      int xLocal = x;
      char v8 = (char) (xLocal ^ b.charAt(0)); // Assuming the first character is used for some kind of offset or key
      
      @Override
      public void run() {
        for(int i = 1; i < b.length(); i++) { // Start from 1, assuming the first char is not to be XOR'd
          v8 = (char) ((xLocal % 255) & 0xFF);
          xLocal += v8;
          
          // Perform XOR on each character in the StringBuilder
          b.setCharAt(i, (char) (b.charAt(i) ^ v8));
        }
      }
    };
  
    // Call the run() method to apply the new algorithm
    run.run();
    
    String s = b.toString();
    if (header.debug) {
      System.out.println("-- parsed <string> \"" + s + "\"");
    }
  
    return new LString(s);
  }


  
  @Override
  public void write(OutputStream out, BHeader header, LString string) throws IOException {
    if(string == LString.NULL) {
      out.write(0);
    } else {
      int len = string.value.length() + 1;
      if(len < 0xFF) {
        out.write((byte)len);
      } else {
        out.write(0xFF);
        header.sizeT.write(out, header, header.sizeT.create(len));
      }
      for(int i = 0; i < string.value.length(); i++) {
        out.write(string.value.charAt(i));
      }
    }
  }
}

class LStringType54 extends LStringType {
  
  @Override
  public LString parse(final ByteBuffer buffer, BHeader header) {
    BInteger sizeT = header.sizeT.parse(buffer, header);
    if(sizeT.asInt() == 0) {
      return LString.NULL;
    }
    final StringBuilder b = this.b.get();
    b.setLength(0);
    sizeT.iterate(new Runnable() {
      
      boolean first = true;
      
      @Override
      public void run() {
        if(!first) {
          b.append((char) (0xFF & buffer.get()));
        } else {
          first = false;
        }
      }
      
    });
    String s = b.toString();
    if(header.debug) {
      System.out.println("-- parsed <string> \"" + s + "\"");
    }
    return new LString(s);
  }
  
  @Override
  public void write(OutputStream out, BHeader header, LString string) throws IOException {
    if(string == LString.NULL) {
      header.sizeT.write(out, header, header.sizeT.create(0));
    } else {
      header.sizeT.write(out, header, header.sizeT.create(string.value.length() + 1));
      for(int i = 0; i < string.value.length(); i++) {
        out.write(string.value.charAt(i));
      }
    }
  }
}

