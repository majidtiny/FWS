package fws_simulator;


import java.util.Random;

import net.wimpi.modbus.net.*;
import net.wimpi.modbus.procimg.*;
//import net.wimpi.modbus.Modbus;
import net.wimpi.modbus.ModbusCoupler;

//java -classpath ../../master/FWS_Master/libs/jamod-1.2.jar:. -Dnet.wimpi.modbus.debug=true fws_simulator.sim
//java -classpath ../../master/FWS_Master/libs/jamod-1.2.jar:.  fws_simulator.sim
public class sim {

	/**
	 * @param args
	 */
	public static void main(String[] args) {
		try {
			ModbusTCPListener listener = null;
			SimpleProcessImage spi = null;
			int port = 30000;

			SimpleRegister reg = new SimpleRegister();
			reg.setValue(101);
			
			SimpleInputRegister reg1 = new SimpleInputRegister();
			reg1.setValue(201);
			
			SimpleInputRegister reg2 = new SimpleInputRegister();
			reg2.setValue(201);
			
			spi = new SimpleProcessImage();
			spi.addRegister(reg);
			spi.addInputRegister(reg1);
			spi.addInputRegister(reg2);
			
			//3. Set the image on the coupler
			ModbusCoupler.getReference().setProcessImage(spi);
			ModbusCoupler.getReference().setMaster(false);
			ModbusCoupler.getReference().setUnitID(15);   
			
			listener = new ModbusTCPListener(3);
			listener.setPort(port);
			listener.start();  
			double x = 0;
			double direction = 180.0;
			Random rg = new Random();
			while(true) {
				
				double rand = Math.random()/8;
				if (rg.nextBoolean())
					rand*=-1;
				double tmp = Math.sin(x)+rand;
				//System.out.println(""+tmp);
				tmp *= 100.0;
				reg1.setValue((short)tmp);
				
				
				/*direction = direction + (Math.random() - 0.5) * 15.0;
	            if (direction < 0.0) {
	                direction = direction + 360.0;
	            }
	            else if (direction > 360.0) {
	                direction = direction - 360.0;
	            }*/
				direction = Math.random()*100*100;
				reg2.setValue((short)direction);
				x+=0.01;
				System.out.println(""+reg1.toShort()+";"+reg2.getValue());
				
				Thread.sleep(1000);
			}
		} catch (Exception e) {
			e.printStackTrace();

		}

	}

}
