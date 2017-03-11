package backup_service.protocols;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.Arrays;

import com.sun.xml.internal.messaging.saaj.util.ByteOutputStream;

import utils.Debug;
import utils.Utilities;

public class MC extends Subprotocol{
	
	public MC(String ipNport, ChannelManager channelManager) throws IOException {
		super(ipNport, channelManager);
	}
	
	@Override
	public void receiveMessage(byte[] message) {
		ByteArrayInputStream bis = new ByteArrayInputStream(message);
		BackupHeader header = readHeaders(bis);	
		if(header.senderID == this.getServerID())
			return;
		Debug.log(this.getConnection().getConnectionInfo().toString(),"Received a message!");
		Debug.log(1,this.getConnection().getConnectionInfo().toString(),"Received:" + header);	
	}
	
	private BackupHeader readHeaders(ByteArrayInputStream bis){
		
		BackupHeader header = null;
		String line = null;
		int i = 0;
		
		while(line == null || !line.equals("")){
			line = Utilities.getLine(bis);
			
			switch(i){
			case 0:
				
				header = new BackupHeader(line);
				break;
			}
			
			i++;
		}
		
		return header;
	}

}
