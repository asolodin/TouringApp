package com.ftdi.javad2xxdemo;

import java.util.Arrays;

import com.ftdi.j2xx.D2xxManager;
import com.ftdi.j2xx.FT_Device;

import android.app.Fragment;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Spinner;
import android.widget.Toast;

//import com.SPIMasterDemo.R.drawable;

public class FT4222SPIMasterFragment extends Fragment {
	
	static Context SPIMasterContext;
	D2xxManager ftdid2xx;
	FT_Device ftDevice = null;
	int DevCount = -1;

	int intRadix = 10;
	
	// handler event
	final int UPDATE_READ_DATA = 0;
	final int UPDATE_WRITE_DATA = 1;
	
	// menu item
	Menu myMenu;
    final int MENU_FORMAT = Menu.FIRST;
    final int MENU_CLEAN = Menu.FIRST+1;
    final String[] formatSettingItems = {"ASCII","Hexadecimal", "Decimal"};
    
	final int FORMAT_ASCII = 0;
	final int FORMAT_HEX = 1;
	final int FORMAT_DEC = 2;
	
	int inputFormat = FORMAT_ASCII;
	StringBuffer readSB = new StringBuffer();
	
	enum ACTION_TYPE{
	   ACTION_READ,
	   ACTION_WRITE
	}
	
    /*graphical objects*/
    EditText readText;
    EditText writeText;
    EditText numBytesText;
    EditText statusText,writeStatusText;
    
    Button readButton, writeButton;
    Button configButton;
    
    Spinner clockPhaseSpinner;
    Spinner clockDividerSpinner;
    Spinner clockFreqSpinner;
    
    /*local variables*/
    byte[] readWriteBuffer;
    byte[] dummyData;
        
    byte numBytes;
    byte status;
    byte clockPhaseMode;
    byte CPOL;
    byte CPHA;
    byte clockFrequence;
    byte CFreq;
    byte clockDivider;
    byte CDivi;

	// Empty Constructor
	public FT4222SPIMasterFragment()
	{
	}

	/* Constructor */
	public FT4222SPIMasterFragment(Context parentContext , D2xxManager ftdid2xxContext)
	{
		SPIMasterContext = parentContext;
		ftdid2xx = ftdid2xxContext;
	}
	
    public int getShownIndex() {
        return getArguments().getInt("index", 9);
    }

	
    /** Called when the activity is first created. */
    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
        Bundle savedInstanceState) {
        if (container == null) {
            return null;
        }
        super.onCreate(savedInstanceState);
        setHasOptionsMenu(true);
        
        View view = inflater.inflate(R.layout.device_ft4222_spi_master, container, false);
                
        /*create editable text objects*/         
        readText = (EditText)view.findViewById(R.id.ReadValues);
        writeText = (EditText)view.findViewById(R.id.WriteValues);
      
               
        numBytesText = (EditText)view.findViewById(R.id.NumberOfBytesValue);
        
       
        statusText=(EditText)view.findViewById(R.id.StatusValues);
        statusText.setInputType(0);
        writeStatusText = (EditText)view.findViewById(R.id.WriteStatusValues);
        writeStatusText.setInputType(0);
         
        readButton = (Button)view.findViewById(R.id.ReadButton);
        writeButton = (Button)view.findViewById(R.id.WriteButton);
        configButton = (Button)view.findViewById(R.id.ConfigButton);
        
        /*allocate buffer*/
        readWriteBuffer = new byte[64];
        dummyData = new byte[64];
        Arrays.fill( dummyData, (byte)(0x00) );
        
        /*default mode is set to mode 0*/
        clockPhaseMode = 0;
        clockPhaseSpinner = (Spinner)view.findViewById(R.id.ClockPhaseValue);
        ArrayAdapter<CharSequence> clockPhaseAdapter = ArrayAdapter.createFromResource(SPIMasterContext,R.array.clock_phase, 
        																	android.R.layout.simple_spinner_item);
        
        clockPhaseSpinner.setAdapter(clockPhaseAdapter);
        clockPhaseSpinner.setSelection(clockPhaseMode);
        

        /*clock frequence*/
        clockFrequence = 0;
        clockFreqSpinner = (Spinner)view.findViewById(R.id.ClockFreqValue);
        ArrayAdapter<CharSequence> clockFreqAdapter = ArrayAdapter.createFromResource(SPIMasterContext,R.array.clock_freq, 
        																	android.R.layout.simple_spinner_item);
        clockFreqSpinner.setAdapter(clockFreqAdapter);
        clockFreqSpinner.setSelection(clockFrequence);
        
        
        /*clock divider*/
        clockDivider = 0;
        clockDividerSpinner = (Spinner)view.findViewById(R.id.ClockDividerValue);
        ArrayAdapter<CharSequence> clockDividerAdapter = ArrayAdapter.createFromResource(SPIMasterContext,R.array.clock_divier, 
        																	android.R.layout.simple_spinner_item);
        
        clockDividerSpinner.setAdapter(clockDividerAdapter);
        clockDividerSpinner.setSelection(clockDivider);
                        
        readButton.setOnClickListener(new View.OnClickListener() {
			
			//@Override
			public void onClick(View v) {
				if(DevCount <= 0)
				{
					msgToast("Please set config before read/wirte data." ,Toast.LENGTH_SHORT);
					return;
				}
				
				intRadix = 10;
				if(FORMAT_HEX == inputFormat)
				{
					intRadix = 16;
				}
				
				if(numBytesText.length() != 0)
				{
					try{
						numBytes = (byte) Integer.parseInt(numBytesText.getText().toString(), intRadix);						
					}
					catch(NumberFormatException ex){
						msgToast("Invalid input for Read Number of Bytes",Toast.LENGTH_SHORT);
						return;						
					}
				
					for(int i=0; i < numBytes; i++){
						readWriteBuffer[i]=(byte)0xff;
					}
				
					// write dummy data and read data
					ftDevice.write(dummyData, numBytes);				

					ftDevice.write(null, 0);
					
					readThread read_thread = new readThread(ACTION_TYPE.ACTION_READ, handler, numBytes+1);
					read_thread.start();
				}
				
			}
		});
        
        /*handle write click*/
		writeButton.setOnClickListener(new View.OnClickListener() {
			
			//@Override
			public void onClick(View v) 
			{
				if(DevCount <= 0)
				{
					msgToast("Please set config before read/wirte data." ,Toast.LENGTH_SHORT);
					return;
				}
					
				intRadix = 10;
				if(FORMAT_HEX == inputFormat)
				{
					intRadix = 16;
				}
				
				
				if(writeText.length() != 0)
				{
					writeData();
				}
			}
		});
        
       /*config section*/ 
        configButton.setOnClickListener(new View.OnClickListener() {
			
			//@Override
			public void onClick(View v) {
				
				clockPhaseMode = (byte)clockPhaseSpinner.getSelectedItemPosition();
				clockFrequence = (byte)clockFreqSpinner.getSelectedItemPosition();
				clockDivider = (byte)clockDividerSpinner.getSelectedItemPosition();
				
				switch(clockPhaseMode)
				{

				case 1:
					CPOL = 0;
					CPHA = 1;
					break;
				case 2:
					CPOL = 1;
					CPHA = 0;
					break;
				case 3:
					CPOL = 1;
					CPHA = 1;
					break;
				case 0:
				default:					
					CPOL = 0;
					CPHA = 0;
					break;
				}
				
				switch(clockFrequence)
				{

				case 1:
					CFreq = 2;
					break;
				case 2:
					CFreq = 0;
					break;
				case 3:
					CFreq = 3;
					break;
				case 0:
				default:					
					CFreq = 1;
					break;
				}
				
				CDivi = (byte)((int)clockDivider + 1);
				
				if(DevCount <= 0)
					ConnectFunction();

				if(DevCount > 0)
				{
					SetConfig(CFreq, CDivi, CPOL, CPHA);
				}
				
			}
		});
        
        return view;
    }
    
	public void ConnectFunction() { 
		int openIndex = 0;

		if (DevCount > 0)
			return;

		DevCount = ftdid2xx.createDeviceInfoList(SPIMasterContext);

		if (DevCount > 0) {
			ftDevice = ftdid2xx.openByIndex(SPIMasterContext, openIndex);

			if(ftDevice == null)
			{
				Toast.makeText(SPIMasterContext,"ftDev == null",Toast.LENGTH_LONG).show();
				return;
			}
			
			if (true == ftDevice.isOpen()) {
				Toast.makeText(SPIMasterContext,
						"devCount:" + DevCount + " open index:" + openIndex,
						Toast.LENGTH_SHORT).show();
			} else {
				Toast.makeText(SPIMasterContext, "Need to get permission!",
						Toast.LENGTH_SHORT).show();
			}
		} else {
			Log.e("j2xx", "DevCount <= 0");
		}
    }
	
	public void SetConfig(int cFreq, int cDivider, int cPol, int cPha)
	{
		/* 49h:Reset/Abort SPI Transaction
		 */	
		int value = 0x49 | 0x00 << 8;
		ftDevice.VendorCmdSet(0x21, value);

		/* 04h: clock frequence
		 * clk_ctl = 00  =>  60Mhz
		 * clk_ctl = 01  =>  24Mhz
		 * clk_ctl = 10  =>  48Mhz
		 * clk_ctl = 11  =>  80Mhz
         */
		value = 0x4 | cFreq << 8;
		ftDevice.VendorCmdSet(0x21, value);
		
		/* 42h: I/O Mode
		 * 1: Single-SPI
		 * 2: Dual-SPI
		 * 4: Quad-SPI
		 */
		value = 0x42 | 0x01 << 8;
		ftDevice.VendorCmdSet(0x21, value);
		
		/* 44h: Clock Divider
		 * 1: 1/2 System Clock
		 * 2: 1/4 System Clock
		 * 3: 1/8 System Clock
		 * 4: 1/16 System Clock
		 * 5: 1/32 System Clock
		 * 6: 1/64 System Clock
		 * 7: 1/128 System Clock
		 * 8: 1/256 System Clock
		 * 9: 1/512 System Clock
		 */
		value = 0x44 | cDivider << 8;
		ftDevice.VendorCmdSet(0x21, value);
		
		/* 45h: CPOL (Clock Polarity) 
		 *  0 or 1
		 */		
		value = 0x45 | cPol << 8;
		ftDevice.VendorCmdSet(0x21, value);
		
		/* 46h: CPHA (Clock Phase) 
		 *  0 or 1
		 */		
		value = 0x46 | cPha << 8;
		ftDevice.VendorCmdSet(0x21, value);
		
		/* 43h: SS Active State 
		 * 0: Active Low
		 * 1: Active High
		 */
		value = 0x43 | 0x00 << 8;
		ftDevice.VendorCmdSet(0x21, value);
		
		/* 48h: Attached Device Map
		 */
		value = 0x48 | 0x01 << 8;
		ftDevice.VendorCmdSet(0x21, value);
		
		value = 0x05 | 0x03 << 8;
		ftDevice.VendorCmdSet(0x21, value);
		
		value = 0xA0 | 0x00 << 8;
		ftDevice.VendorCmdSet(0x21, value);
		
		
		/* 05H: select function
		 * 1: I2C master
		 * 2: I2C slave
		 * 3: SPI master
		 * 4: SPI slave
		 */
		value = 0x05 | 0x03 << 8;
		ftDevice.VendorCmdSet(0x21, value);
		
		value = 0x42 | 0x01 << 8;
		ftDevice.VendorCmdSet(0x21, value);
		
		/* 4Ah: Restart SPI Controller
		 */
		value = 0x4A | 0x01 << 8;
		ftDevice.VendorCmdSet(0x21, value);
		
		Toast.makeText(SPIMasterContext, "cFreq:"+cFreq+ " cDivider:"+cDivider+" cPol:"+cPol+" cPha:"+cPha+"\n"+
		               " Config Done~~~",Toast.LENGTH_SHORT).show();
	}
	
	@Override
	public void onCreateOptionsMenu(Menu menu, MenuInflater inflater) {
		myMenu = menu;
		myMenu.add(0, MENU_FORMAT, 0, "Format - ASCII");
		myMenu.add(0, MENU_CLEAN, 0, "Reset");		
		super.onCreateOptionsMenu(myMenu, inflater);
	}	

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch(item.getItemId())
        {
        case MENU_FORMAT:
        	new AlertDialog.Builder(SPIMasterContext).setTitle("Data Format")
			.setItems(formatSettingItems, new DialogInterface.OnClickListener() 
			{
				public void onClick(DialogInterface dialog, int which)
				{	
					MenuItem item = myMenu.findItem(MENU_FORMAT);
					if(0 == which)
					{						
						inputFormat = FORMAT_ASCII;
					    item.setTitle("Format - "+ formatSettingItems[0]);
					}
					else if(1 == which)
					{
						inputFormat = FORMAT_HEX;
						item.setTitle("Format - "+ formatSettingItems[1]);
					}
					else
					{
						inputFormat = FORMAT_DEC;
						item.setTitle("Format - "+ formatSettingItems[2]);						
					}
				    char[] ch = new char[1];
				    appendData(ch, 0);
				}
			}).show();           	
        	
        	break;
        	
        case MENU_CLEAN:
        default:        	
        	readSB.delete(0, readSB.length());
        	readText.setText(readSB);
        	break;
        }
 
        return super.onOptionsItemSelected(item);
    }
    
    
    public void writeData()
    {   
    	String srcStr = writeText.getText().toString();
    	String destStr = "";
    	switch(inputFormat)
    	{
    	case FORMAT_HEX:
    		{
				String[] tmpStr = srcStr.split(" ");
				
				for(int i = 0; i < tmpStr.length; i++)
				{
					if(tmpStr[i].length() == 0)
					{
						msgToast("Incorrect input for HEX format."
								+"\nThere should be only 1 space between 2 HEX words.",Toast.LENGTH_SHORT);
						return;
					}					
					else if(tmpStr[i].length() != 2)
					{
						msgToast("Incorrect input for HEX format."
								+"\nIt should be 2 bytes for each HEX word.",Toast.LENGTH_SHORT);
						return;
					}						
				}
				
				try	
				{
					destStr = hexToAscii(srcStr.replaceAll(" ", ""));
				}
				catch(IllegalArgumentException e)
				{
					msgToast("Incorrect input for HEX format."
						    +"\nAllowed charater: 0~9, a~f and A~F",Toast.LENGTH_SHORT);
					return;
				}
    		}
    		break;
    		
    	case FORMAT_DEC:
    		{
				String[] tmpStr = srcStr.split(" ");
				
				for(int i = 0; i < tmpStr.length; i++)
				{
					if(tmpStr[i].length() == 0)
					{
						msgToast("Incorrect input for DEC format."
								+"\nThere should be only 1 space between 2 DEC words.",Toast.LENGTH_SHORT);
						return;
					}					
					else if(tmpStr[i].length() != 3)
					{
						msgToast("Incorrect input for DEC format."
								+"\nIt should be 3 bytes for each DEC word.",Toast.LENGTH_SHORT);
						return;
					}						
				}
				
				try
				{
					destStr = decToAscii(srcStr.replaceAll(" ", ""));
				}
				catch(IllegalArgumentException e)
				{	
					if(e.getMessage().equals("ex_a"))
					{
						msgToast("Incorrect input for DEC format."
								+"\nAllowed charater: 0~9",Toast.LENGTH_SHORT);					
					}
					else
					{
						msgToast("Incorrect input for DEC format."
							    +"\nAllowed range: 0~255",Toast.LENGTH_SHORT);
					}					
					return;
				}
    		}
    		break;
    		
    	case FORMAT_ASCII:    		
    	default:
    		destStr = srcStr;
    		break;
    	}	
    	
		numBytes = (byte)destStr.length();
		for (int i = 0; i < numBytes; i++) {
			readWriteBuffer[i] = (byte)destStr.charAt(i);
		}
		

		int ret = ftDevice.write(readWriteBuffer, numBytes);				
		msgToast("write ret:" + ret + " numBytes:"+numBytes,Toast.LENGTH_SHORT);

		writeStatusText.setText(Integer.toString(numBytes, intRadix));
		
		ret = ftDevice.write(null, 0);
		if(ret < 0)
		{
			msgToast("write null zero lenght data fail.",Toast.LENGTH_SHORT);
		}
		
		readThread read_thread = new readThread(ACTION_TYPE.ACTION_WRITE, handler, numBytes+1);
		read_thread.start();
    }
    
	String hexToAscii(String s) throws IllegalArgumentException
	{
		  int n = s.length();
		  StringBuilder sb = new StringBuilder(n / 2);
		  for (int i = 0; i < n; i += 2)
		  {
		    char a = s.charAt(i);
		    char b = s.charAt(i + 1);
		    sb.append((char) ((hexToInt(a) << 4) | hexToInt(b)));
		  }
		  return sb.toString();
	}
	
	static int hexToInt(char ch)
	{
		  if ('a' <= ch && ch <= 'f') { return ch - 'a' + 10; }
		  if ('A' <= ch && ch <= 'F') { return ch - 'A' + 10; }
		  if ('0' <= ch && ch <= '9') { return ch - '0'; }
		  throw new IllegalArgumentException(String.valueOf(ch));
	}

	String decToAscii(String s) throws IllegalArgumentException
	{
		
		int n = s.length();
		boolean pause = false;
		StringBuilder sb = new StringBuilder(n / 2);
		for (int i = 0; i < n; i += 3)
		{
			char a = s.charAt(i);
			char b = s.charAt(i + 1);
			char c = s.charAt(i + 2);
			int val = decToInt(a)*100 + decToInt(b)*10 + decToInt(c);
			if(0 <= val && val <= 255)
			{
				sb.append((char) val);
			}
			else
			{
				pause = true;
				break;
			}
		}
		
		if(false == pause)
			return sb.toString();
		throw new IllegalArgumentException("ex_b");
	}
	
	static int decToInt(char ch)
	{
		  if ('0' <= ch && ch <= '9') { return ch - '0'; }
		  throw new IllegalArgumentException("ex_a");
	}
	
    void msgToast(String str, int showTime)
    {
    	Toast.makeText(SPIMasterContext, str, showTime).show();
    }
    
    public void appendData(String s)
    {
    	switch(inputFormat)
    	{
    	case FORMAT_HEX:
    		{
    			readText.append("Hex");    		
    		}
    		break;
    		
    	case FORMAT_DEC:
    		{
    			readText.append("Dec");
    		}
    		break;
    		
    	case FORMAT_ASCII:    		
    	default:
    		readText.append(s);
    		break;
    	}
    }
    
    public void appendData(char[] data, int len)
    {
    	if(len >= 1)    		
    		readSB.append(String.copyValueOf(data, 0, len));
    	
    	switch(inputFormat)
    	{
    	case FORMAT_HEX:
    		{
    			char[] ch = readSB.toString().toCharArray();
    			String temp;
    			StringBuilder tmpSB = new StringBuilder();
    			for(int i = 0; i < ch.length; i++)
    			{
    				temp = String.format("%02x", (int) ch[i]);

    				if(temp.length() == 4)
    				{
    					tmpSB.append(temp.substring(2, 4));
    				}
    				else
    				{
    					tmpSB.append(temp);
    				}

   					if(i+1 < ch.length)
   					{
   						tmpSB.append(" ");	
   					}
    			}
    			readText.setText(tmpSB);
    			tmpSB.delete(0, tmpSB.length());
    		}
    		break;
    		
    	case FORMAT_DEC:
    		{
    			char[] ch = readSB.toString().toCharArray();
    			String temp;
    			StringBuilder tmpSB = new StringBuilder();
    			for(int i = 0; i < ch.length; i++)
    			{   				
    				temp = Integer.toString((int)(ch[i] & 0xff));
    				for(int j = 0; j < (3 - temp.length()); j++)
    				{
    					tmpSB.append("0");
    				}
   					tmpSB.append(temp);
   					
   					if(i+1 < ch.length)
   					{
   						tmpSB.append(" ");	
   					}
    			}    			
    			readText.setText(tmpSB);
    			tmpSB.delete(0, tmpSB.length());    			
    		}
    		break;
    		
    	case FORMAT_ASCII:    		
    	default:
    		readText.setText(readSB);
    		break;
    	}
    }
    
	final Handler handler =  new Handler()
    {
    	@Override
    	public void handleMessage(Message msg)
    	{
    		switch(msg.what)
			{
			case UPDATE_READ_DATA:
				
				//read the bytes from the text box
				char [] displayReadbuffer;
				displayReadbuffer = new char[64];
				int displayActualNumBytes;
				displayActualNumBytes = ((Integer)(msg.obj)).intValue();

				for(int i = 0; i < displayActualNumBytes ; i++)
				{
					displayReadbuffer[i] = (char)readWriteBuffer[i];
				}
				
				appendData(displayReadbuffer, displayActualNumBytes);

				statusText.setText(Integer.toString(displayActualNumBytes, intRadix));
				break;
			case UPDATE_WRITE_DATA:
				//TODO
				break;
    		}
    	}
    };
    
    private class readThread  extends Thread
	{
		Handler mHandler;
		int readLen;
		ACTION_TYPE actType;
		
		readThread(ACTION_TYPE type, Handler h, int len){
			readLen = len;
			mHandler = h;
			actType = type;
			this.setPriority(Thread.MIN_PRIORITY);
		}

		@Override
		public void run()
		{
			int i = 0;
			int iGetData = 0;
			byte[] readBuf = new byte[64];
			int bufIndex = 0;

			//Log.e("read thread","wait readLen:"+readLen);

			int iavailable = 0;
			while(iGetData < readLen)
			{
				i++;
				if(i > 10)  // 10 * 50 = 500 (500ms)
				{
					Log.e("read thread","wait too long, no data, return");
					break;
				}
				
				try {
					Thread.sleep(50);
				} catch (InterruptedException e) {
				}

				synchronized(ftDevice)
				{
					// check the amount of available data
					iavailable = ftDevice.getQueueStatus();
					//Log.e("read thread","wait iavailable:"+iavailable);
					if (iavailable > 0) {
						iGetData += iavailable;
						// get the data			
						Arrays.fill( readBuf, (byte)(0x00) );
						ftDevice.read(readBuf, iavailable);
						//Log.e("read thread","iGetData:"+ iGetData);
						
						for(int j = 0; j < iavailable; j++)
						{
							//Log.e("SPI read","j:"+j+"f "+readBuf[j]);
							readWriteBuffer[bufIndex] = readBuf[j];
							bufIndex++;
						}

					}
				}
			}
			
			//Log.e("SPI read","iGetData:"+iGetData+ " bufIndex:"+bufIndex);
			
			Integer a1 = new Integer(iGetData);
			Message msg;
			if(ACTION_TYPE.ACTION_READ == actType || iGetData > 0)
			{
				msg = mHandler.obtainMessage(UPDATE_READ_DATA, a1);
			}
			else
			{
				msg = mHandler.obtainMessage(UPDATE_WRITE_DATA, a1);
			}
			mHandler.sendMessage(msg);
		}

	}
}