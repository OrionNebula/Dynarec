package net.lotrek.dynarec.devices;

import java.awt.Font;
import java.awt.FontFormatException;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;

import org.lwjgl.LWJGLException;
import org.lwjgl.input.Mouse;
import org.lwjgl.opengl.Display;
import org.lwjgl.opengl.DisplayMode;
import org.lwjgl.opengl.GL11;

import static org.lwjgl.opengl.GL11.*;

import org.newdawn.slick.Color;
import org.newdawn.slick.TrueTypeFont;

public class VideoDevice extends MemorySpaceDevice
{
	public static final int RENDER_TEXT = 0, RENDER_RAW = 1, INITAL_WIDTH = 1280/2, INITIAL_HEIGHT = 960/2;
	
	private Structure controlStructure = new Structure(Byte.class, Integer.class, Integer.class, Integer.class), pixelStructure = new Structure(Byte.class, Integer.class, Byte.class, Byte.class), queryStructure = new Structure(Byte.class, Integer.class);
	private Register[] instanceRegisters;
	private TrueTypeFont font;
	private int renderMode;
	private char[][] screenText = new char[32][64];
	private Color[][] screenColors = new Color[32][64];
	
	public int getOccupationLength()
	{
		return controlStructure.getLength();
	}

	public void executeDeviceCycle()
	{
		if(Display.isCreated())
		{
			int statusReg = (int)(byte)instanceRegisters[0].getValue();
			renderMode = statusReg >> 2 & 0x3;
			
			int w = (int) instanceRegisters[1].getValue(), h = (int) instanceRegisters[2].getValue();
			if(w != Display.getWidth() || h != Display.getHeight())
				try {
					Display.setDisplayMode(new DisplayMode(w, h));
				} catch (LWJGLException e) {
					e.printStackTrace();
				}
			
			if((statusReg & 1) == 1)
			{
				int instAddr = (int)instanceRegisters[3].getValue();
				if(instAddr != 0)
				{
					int len = (int) Register.getTypeForBytes(Integer.class, this.getProcessor().getMemory(), instAddr);
					for (int i = 0; i < len; i++)
					{
						Register[] str = pixelStructure.getInstance(instAddr + 4 + i * pixelStructure.getLength(), this.getProcessor().getMemory());
						
						int x = (byte) str[2].getValue(), y = (byte) str[3].getValue();
						
//						System.out.printf("type: %d; x: %d; y: %d; data: %s;\n",(byte)str[0].getValue(), x, y, (byte)str[0].getValue() == 0 ? "" + (char)(int)str[1].getValue() : "" + (int)str[1].getValue());
						if((byte)str[0].getValue() == 0)
							screenText[y][x] = (char)(int)str[1].getValue();
						if((byte)str[0].getValue() == 1)
							screenColors[y][x] = new Color((int)str[1].getValue());
						if((byte)str[0].getValue() == 2)
						{
							if(x < 0 || x > 63 || y < 0 || y > 31)
							{
								Register[] pix = queryStructure.getInstance(str[1].getValue(Integer.class), this.getProcessor().getMemory());
								pix[0].setValue(Byte.class, (byte)-1);
								pix[1].setValue(Integer.class, 0);
							}else
							{
								Register[] pix = queryStructure.getInstance(str[1].getValue(Integer.class), this.getProcessor().getMemory());
								pix[0].setValue(Byte.class, (byte)screenText[y][x]);
								pix[1].setValue(Integer.class, screenColors[y][x] == null ? 0xffffff : (screenColors[y][x].getAlphaByte() << 24) | (screenColors[y][x].getRedByte() << 16) | (screenColors[y][x].getGreenByte() << 8) | screenColors[y][x].getBlueByte());
							}
						}
						if((byte)str[0].getValue() == 3)
						{
							screenText = new char[32][64];
							screenColors = new Color[32][64];
						}
					}
				}
				
				statusReg &= ~1;
				instanceRegisters[0].setValue(Byte.class, (byte)statusReg);
			}
			
			switch(renderMode)
			{
			case RENDER_TEXT:
				glClear(GL_COLOR_BUFFER_BIT | GL_DEPTH_BUFFER_BIT);
				
				for (int y = 0; y < screenText.length; y++)
					for (int x = 0; x < screenText[y].length; x++)
						font.drawString(x * (Display.getWidth() / 64), y * (Display.getHeight() / 32), "" + screenText[y][x], screenColors[y][x] == null ? Color.white : screenColors[y][x]);
				
				GL11.glDisable(GL_TEXTURE_2D);
				
				GL11.glBegin(GL_QUADS);
					GL11.glVertex2i(Mouse.getX(), Display.getHeight() - Mouse.getY());
					GL11.glVertex2i(Mouse.getX() + 5, Display.getHeight() -  Mouse.getY());
					GL11.glVertex2i(Mouse.getX() + 5, Display.getHeight() -  Mouse.getY() + 5);
					GL11.glVertex2i(Mouse.getX(), Display.getHeight() -  Mouse.getY() + 5);
				GL11.glEnd();
				
				GL11.glEnable(GL_TEXTURE_2D);
				
				Display.update();
				break;
			case RENDER_RAW:
				glClear(GL_COLOR_BUFFER_BIT | GL_DEPTH_BUFFER_BIT);
				
				/*for (int y = 0; y < screenText.length; y++)
					for (int x = 0; x < screenText[y].length; x++)
						font.drawString(x * (Display.getWidth() / 64), y * (Display.getHeight() / 32), "" + screenText[y][x], screenColors[y][x] == null ? Color.white : screenColors[y][x]);*/
				
				font.drawString(0, 0, "Drawing!");
				
				Display.update();
				
				break;
			}
			
			if(Display.isCloseRequested())
				Display.destroy();
		}else
			System.exit(0);
	}

	public void initializeDevice()
	{
		instanceRegisters = controlStructure.getInstance(getOccupationAddr(), this.getProcessor().getMemory());
		
		instanceRegisters[1].setValue(Integer.class, INITAL_WIDTH);
		instanceRegisters[2].setValue(Integer.class, INITIAL_HEIGHT);
		
		try {
			Display.setTitle(String.format("%s VideoDevice; %d bytes of memory; %d bytes usable", this.getProcessor().getClass().getSimpleName(), this.getProcessor().getMemorySize(), this.getProcessor().getAvailableMemory()));
			Display.setDisplayMode(new DisplayMode(INITAL_WIDTH, INITIAL_HEIGHT));
			Display.create();
			Mouse.setGrabbed(true);
			font = new TrueTypeFont(Font.createFont(Font.TRUETYPE_FONT, new FileInputStream(new File("./res/Perfect DOS VGA 437 Win.ttf"))).deriveFont(Font.PLAIN, Display.getHeight() / 32), false);
			glEnable(GL_TEXTURE_2D);
			glEnable(GL_BLEND);
			glBlendFunc(GL_SRC_ALPHA, GL_SRC_ALPHA);
			glMatrixMode(GL_PROJECTION_MATRIX);
				glOrtho(0, Display.getWidth(), Display.getHeight(), 0, -1, 1);
			glMatrixMode(GL_MODELVIEW_MATRIX);
		} catch (LWJGLException | FontFormatException | IOException e) {
			e.printStackTrace();
		}
		
		System.out.println("Video Device initialized");
	}

}
