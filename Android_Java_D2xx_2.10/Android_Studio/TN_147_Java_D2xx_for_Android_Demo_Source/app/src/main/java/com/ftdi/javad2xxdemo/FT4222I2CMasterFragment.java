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

public class FT4222I2CMasterFragment extends Fragment {
	
	static Context I2CMasterContext;
	D2xxManager ftdid2xx;
	FT_Device ftDevice = null;
	int DevCount = -1;
	
	// handler event
	final int UPDATE_READ_DATA = 0;
	
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
	
	int intRadix = 10;

    /*graphical objects*/
    EditText readText;
    EditText writeText;
    EditText addrText;
    EditText numBytesText;
    EditText statusText,writeStatusText;
    
    Button readButton, writeButton;
    Button configButton;
    
    Spinner i2cFreqSpinner;
    
    /*local variables*/
    byte[] readWriteBuffer;
        
    byte numBytes;
    byte status;
    byte i2cFrequence;
    byte deviceAddress;

	// Empty Constructor
	public FT4222I2CMasterFragment()
	{
	}

	/* Constructor */
	public FT4222I2CMasterFragment(Context parentContext , D2xxManager ftdid2xxContext)
	{
		I2CMasterContext = parentContext;
		ftdid2xx = ftdid2xxContext;
	}
	
    public int getShownIndex() {
        return getArguments().getInt("index", 10);
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
        
        View view = inflater.inflate(R.layout.device_ft4222_i2c_master, container, false);
        
        /*create editable text objects*/         
        readText = (EditText)view.findViewById(R.id.ReadValues);
        writeText = (EditText)view.findViewById(R.id.WriteValues);
        addrText = (EditText)view.findViewById(R.id.DeviceAddressValue);
      
               
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

        /*clock frequence*/
        i2cFrequence = 0;
        i2cFreqSpinner = (Spinner)view.findViewById(R.id.ClockFreqValue);
        ArrayAdapter<CharSequence> clockFreqAdapter = ArrayAdapter.createFromResource(I2CMasterContext,R.array.I2C_freq, 
        																	android.R.layout.simple_spinner_item);
        i2cFreqSpinner.setAdapter(clockFreqAdapter);
        i2cFreqSpinner.setSelection(i2cFrequence);
       
                        
        readButton.setOnClickListener(new View.OnClickListener() {
			
			//@Override
			public void onClick(View v) {
				if(DevCount <= 0)
				{
					msgToast("Please set config before read/wirte data." ,Toast.LENGTH_SHORT);
					return;
				}
								
				int intRadix = 10;
				
				if(FORMAT_HEX == inputFormat)
				{
					intRadix = 16;
				}
				
				if(addrText.length() != 0)
				{
					try{
						deviceAddress = (byte) Integer.parseInt(addrText.getText().toString(), 16);						
					}
					catch(NumberFormatException ex){
						msgToast("Invalid input for device address",Toast.LENGTH_SHORT);
						return;						
					}
				}
				else
				{
					msgToast("Please set device address",Toast.LENGTH_SHORT);
					return;					
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
				
					/* read data header: 4 bytes
					 * (address << 1) + 1, reserve, 2 btyes for data length 
					 */				
					int bufIndex = 0;
					readWriteBuffer[bufIndex++] = (byte)((deviceAddress << 1) + 1);
					Log.e("c","read addr:"+readWriteBuffer[bufIndex-1]);
					readWriteBuffer[bufIndex++] = 0x00;
					readWriteBuffer[bufIndex++] = 0x00;
					readWriteBuffer[bufIndex++] = numBytes;
					ftDevice.write(readWriteBuffer, bufIndex);
					
					readThread read_thread = new readThread(handler, numBytes);
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

			    byte clockFreq;
			    byte I2CMTP;
			    
				i2cFrequence = (byte)i2cFreqSpinner.getSelectedItemPosition();
				
				switch(i2cFrequence)
				{
				case 1: // 100K 48M 59
					clockFreq = 2;
					I2CMTP = (byte)0x3B;
					break;
				case 2: // 400K 48M 211
					clockFreq = 2;
					I2CMTP = (byte)0xD3;
					break;
				case 3: // 1M 48M 199 
					clockFreq = 2;					
					I2CMTP = (byte)0xC7;
					break;
				case 4: // 3.4M 60M 130
					clockFreq = 0;
					I2CMTP = (byte)0x82;
					break;
					
				case 0:  // 60K 48M 99
				default:					
					clockFreq = 2;
					I2CMTP = (byte)0x63;
					break;
				}
				
				if(DevCount <= 0)
					ConnectFunction();

				if(DevCount > 0)
				{
					SetConfig(clockFreq, I2CMTP);
				}
				
			}
		});
        
        return view;
    }
    
	public void ConnectFunction() {
		int openIndex = 0;

		if (DevCount > 0)
			return;

		DevCount = ftdid2xx.createDeviceInfoList(I2CMasterContext);

		if (DevCount > 0) {
			ftDevice = ftdid2xx.openByIndex(I2CMasterContext, openIndex);

			if(ftDevice == null)
			{
				Toast.makeText(I2CMasterContext,"ftDev == null",Toast.LENGTH_LONG).show();
				return;
			}
			
			if (true == ftDevice.isOpen()) {
				Toast.makeText(I2CMasterContext,
						"devCount:" + DevCount + " open index:" + openIndex,
						Toast.LENGTH_SHORT).show();
			} else {
				Toast.makeText(I2CMasterContext, "Need to get permission!",
						Toast.LENGTH_SHORT).show();
			}
		} else {
			Log.e("j2xx", "DevCount <= 0");
		}
    }
	
	public void SetConfig(byte cFreq, byte i2cmtp)
	{
		// 51h:	Reset/Abort I2C Master
		int value = 0x51 | 0x00 << 8;
		ftDevice.VendorCmdSet(0x21, value);

		/* 04h: clock frequence
		 * clk_ctl = 00  =>  60Mhz
		 * clk_ctl = 01  =>  24Mhz
		 * clk_ctl = 10  =>  48Mhz
		 * clk_ctl = 11  =>  80Mhz
         */
		value = 0x4 | cFreq << 8;
		ftDevice.VendorCmdSet(0x21, value);
				
		/* 05H: select function
		 * 1: I2C master
		 * 2: I2C slave
		 * 3: SPI master
		 * 4: SPI slave
		 */
		value = 0x05 | 0x01 << 8;
		ftDevice.VendorCmdSet(0x21, value);
				
		// 52h:	I2CMTP - TIMER PERIOD REGISTER
		value = 0x52 | i2cmtp << 8;
		ftDevice.VendorCmdSet(0x21, value);		
		
		Toast.makeText(I2CMasterContext, "cFreq:"+cFreq+ " tmp:" + i2cmtp + "\n"+
		               " Config Done~~~",Toast.LENGTH_SHORT).show();
	}
	
	@Override
	public void onCreateOptionsMenu(Menu menu, MenuInflater inflater) {
		myMenu = menu;
		myMenu.add(0, MENU_FORMAT, 0, "Format - ASCII");
		myMenu.add(0, MENU_CLEAN, 0, "Reset");		
		super.onCreateOptionsMenu(myMenu, inflater);
	}

    public boolean onOptionsItemSelected(MenuItem item) {    
        switch(item.getItemId())
        {
        case MENU_FORMAT:
        	new AlertDialog.Builder(I2CMasterContext).setTitle("Data Format")
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
			writeText.setText("");
			addrText.setText("");
			numBytesText.setText("");
			statusText.setText("");
			writeStatusText.setText("");				
			i2cFreqSpinner.setSelection(0);
        	break;
        }
 
        return false;
    }
    
    
    public void writeData()
    {    	 
		if(addrText.length() != 0)
		{
			try{
				deviceAddress = (byte) Integer.parseInt(addrText.getText().toString(), 16);						
			}
			catch(NumberFormatException ex){
				msgToast("Invalid input for device address",Toast.LENGTH_SHORT);
				return;						
			}
		}
		else
		{
			msgToast("Please set device address",Toast.LENGTH_SHORT);
			return;					
		}
		
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

		int ret = -1;
		int bufIndex = 0;
		
		/* write data header: 4 bytes
		 * (address << 1), reserve, 2 bytes for data length 
		 */
		readWriteBuffer[bufIndex++] = (byte)(deviceAddress << 1);
		Log.e("c","write addr:"+readWriteBuffer[bufIndex-1]);
		readWriteBuffer[bufIndex++] = 0x00;
		readWriteBuffer[bufIndex++] = 0x00;
		readWriteBuffer[bufIndex++] = numBytes;
		
		for (int i = 0; i < numBytes; i++) {
			readWriteBuffer[bufIndex++] = (byte)srcStr.charAt(i);
		}
		
		ret = ftDevice.write(readWriteBuffer, bufIndex);		
		msgToast("write ret:" + ret + " index:"+bufIndex,Toast.LENGTH_SHORT);
		
		writeStatusText.setText(Integer.toString(numBytes, intRadix));
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
    	Toast.makeText(I2CMasterContext, str, showTime).show();
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
				
				Log.e("handler","displayActualNumBytes:"+displayActualNumBytes);
				
				appendData(displayReadbuffer, displayActualNumBytes);

				statusText.setText(Integer.toString(displayActualNumBytes, intRadix));
				break;

    		}
    	}
    };
    
    private class readThread  extends Thread
	{
		Handler mHandler;
		int readLen;

		readThread(Handler h, int len){
			readLen = len;
			mHandler = h;
			this.setPriority(Thread.MIN_PRIORITY);
		}

		@Override
		public void run()
		{
			int i = 0;
			int iGetData = 0;
			byte[] readBuf = new byte[64];
			int bufIndex = 0;

			Log.e("read thread","wait readLen:"+readLen);
			
			int iavailable = 0;
			while(iGetData < readLen)
			{
				i++;
				if(i > 10) // 10 * 50 = 500 (500ms)
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
					Log.e("read thread","wait iavailable:"+iavailable);
					if (iavailable > 0) {
						iGetData += iavailable;
						// get the data					
						Arrays.fill( readBuf, (byte)(0x00) );
						ftDevice.read(readBuf, iavailable);
						Log.e("read thread","iGetData:"+ iGetData);
						
						for(int j = 0; j < iavailable; j++)
						{
							readWriteBuffer[bufIndex] = readBuf[j];
							bufIndex++;
						}

					}
				}
			}
			Integer a1 = new Integer(iGetData);
			Message msg = mHandler.obtainMessage(UPDATE_READ_DATA, a1);
			mHandler.sendMessage(msg);
		}

	}
}