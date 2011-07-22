package dasher.applet;

import java.awt.Graphics;

import javax.swing.*;
import java.awt.Image;

public class JDasherPanel extends JPanel {

	private JDasherThread worker;
	
	public JDasherPanel(JDasherThread worker) {
		this.worker = worker;
	}
	
	protected void paintComponent(Graphics g) {
		worker.setSize(this.getWidth(), this.getHeight());
		Image buffer = worker.getCurrentFrontbuffer();
		g.drawImage(buffer, 0, 0, null);	
	}
}
