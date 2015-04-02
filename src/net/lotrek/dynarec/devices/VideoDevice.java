package net.lotrek.dynarec.devices;

import java.awt.Font;
import java.awt.FontFormatException;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;

import org.lwjgl.LWJGLException;
import org.lwjgl.opengl.Display;
import org.lwjgl.opengl.DisplayMode;

import static org.lwjgl.opengl.GL11.*;

import org.newdawn.slick.Color;
import org.newdawn.slick.TrueTypeFont;

public class VideoDevice extends MemorySpaceDevice
{
	public static final int RENDER_TEXT = 0, RENDER_RAW = 1, INITAL_WIDTH = 1280, INITIAL_HEIGHT = 960;
	
	private Structure controlStructure = new Structure(Byte.class, Integer.class, Integer.class, Integer.class), pixelStructure = new Structure(Integer.class, Byte.class, Short.class, Short.class);
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
		int statusReg = (int)(byte)instanceRegisters[0].getValue();
		renderMode = statusReg >> 2 & 0x3;
		
		int w = (int) instanceRegisters[1].getValue(), h = (int) instanceRegisters[2].getValue();
		if(w != Display.getWidth() || h != Display.getHeight())
			try {
//				System.out.println(Display.getWidth() + " x " + Display.getHeight() + " : " + w + " x " + h);
				Display.setDisplayMode(new DisplayMode(w, h));
			} catch (LWJGLException e) {
				e.printStackTrace();
			}
		
		if((statusReg & 1) == 1)
		{
			int instAddr = (int)instanceRegisters[3].getValue();
			if(instAddr != 0)
			{
				instanceRegisters[3].setValue(Integer.class, 0);
				
				int len = (int) Register.getTypeForBytes(Integer.class, this.getProcessor().getMemory(), instAddr);
				for (int i = 0; i < len; i++)
				{
					Register[] str = pixelStructure.getInstance(instAddr + 4 + i * 9, this.getProcessor().getMemory());
					
					short x = (short) str[2].getValue(), y = (short) str[3].getValue();
					screenColors[y][x] = new Color((int)str[0].getValue());
					screenText[y][x] = (char)(byte)str[1].getValue();
				}
			}
			
			statusReg &= ~3;
			statusReg |= 2;
			instanceRegisters[0].setValue(Byte.class, (byte)statusReg);
		}
		
		if(Display.isCreated())
		{
			switch(renderMode)
			{
			case RENDER_TEXT:
				glClear(GL_COLOR_BUFFER_BIT | GL_DEPTH_BUFFER_BIT);
				
				for (int y = 0; y < screenText.length; y++)
					for (int x = 0; x < screenText[y].length; x++)
						font.drawString(x * (Display.getWidth() / 64), y * (Display.getHeight() / 32), "" + screenText[y][x], screenColors[y][x] == null ? Color.white : screenColors[y][x]);
				
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
		}
	}

	public void initializeDevice()
	{
		instanceRegisters = controlStructure.getInstance(getOccupationAddr(), this.getProcessor().getMemory());
		
		instanceRegisters[1].setValue(Integer.class, INITAL_WIDTH);
		instanceRegisters[2].setValue(Integer.class, INITIAL_HEIGHT);
		
		try {
			Display.setDisplayMode(new DisplayMode(INITAL_WIDTH, INITIAL_HEIGHT));
			Display.create();
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
	}

}
