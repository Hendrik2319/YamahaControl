package net.schwarzbaer.java.tools.yamahacontrol;

import java.awt.BorderLayout;
import java.awt.FlowLayout;
import java.awt.Frame;
import java.awt.event.ActionListener;
import java.io.BufferedReader;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.util.Vector;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JPanel;

public class Config {

	private static final String CONFIG_FILENAME = "YamahaControl.cfg";

	static Vector<String> knownAddresses = new Vector<>();
	static String lastSelectedAddress = null;

	
	private static void clearConfig() {
		knownAddresses.clear();
	}

	static void readConfig() {
		clearConfig();
		try (BufferedReader config = new BufferedReader( new InputStreamReader( new FileInputStream(CONFIG_FILENAME), StandardCharsets.UTF_8 ) )) {
			String line;
			while ( (line=config.readLine())!=null ) {
				if (line.startsWith("knownAddress="))
					knownAddresses.add(line.substring("knownAddress=".length()));
				if (line.startsWith("lastSelected="))
					knownAddresses.add(lastSelectedAddress = line.substring("lastSelected=".length()));
			}
		}
		catch (FileNotFoundException e) {}
		catch (IOException e) {
			e.printStackTrace();
		}
	}
	
	static void writeConfig() {
		try (PrintWriter config = new PrintWriter( new OutputStreamWriter( new FileOutputStream(CONFIG_FILENAME), StandardCharsets.UTF_8 ) )) {
			for (String addr:knownAddresses)
				if (!addr.equals(lastSelectedAddress))
					config.printf("knownAddress=%s%n", addr);
			if (lastSelectedAddress!=null)
				config.printf("lastSelected=%s%n", lastSelectedAddress);
		}
		catch (FileNotFoundException e) {}
	}
	
	static String selectAddress(Frame window) {
		String addr = new SelectAddressDialog(window).getValue();
		if (addr!=null && !knownAddresses.contains(addr)) {
			knownAddresses.add(addr);
			knownAddresses.sort(null);
		}
		lastSelectedAddress = addr;
		writeConfig();
		return addr;
	}

	private static final class SelectAddressDialog extends JDialog {
		private static final long serialVersionUID = 5517438807170911577L;
		
		private String selectedValue;
		private JComboBox<String> knownIPsCmbBx;
		
		SelectAddressDialog(Frame parent) {
			super(parent,"Select Address",true);
			selectedValue = null;
			
			JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT,3,3));
			buttonPanel.add((JButton)createButton("Ok",e->confirm(true)));
			buttonPanel.add((JButton)createButton("Cancel",e->confirm(false)));
			
			knownIPsCmbBx = new JComboBox<>(knownAddresses);
			knownIPsCmbBx.setEditable(true);
			knownIPsCmbBx.setSelectedItem(lastSelectedAddress);
			
			JPanel contentPane = new JPanel(new BorderLayout(3,3));
			contentPane.setBorder(BorderFactory.createEmptyBorder(5,10,5,10));
			contentPane.add(new JLabel("Select or enter address of your receiver."),BorderLayout.NORTH);
			contentPane.add(new JLabel("Address: "),BorderLayout.WEST);
			contentPane.add(knownIPsCmbBx,BorderLayout.CENTER);
			contentPane.add(buttonPanel,BorderLayout.SOUTH);
			
			setContentPane(contentPane);
			pack();
			setMinimumSize(getSize());
			setLocationRelativeTo(parent);
		}
		
		private void confirm(boolean isOk) {
			if (isOk) {
				Object selected = knownIPsCmbBx.getSelectedItem();
				selectedValue = selected==null?null:selected.toString();
			}
			setVisible(false);
		}

		private JButton createButton(String title, ActionListener l) {
			JButton button = new JButton(title);
			button.addActionListener(l);
			return button;
		}

		public String getValue() {
			setVisible(true);
			return selectedValue;
		}
		
		
	}
	
}
