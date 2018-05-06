package net.schwarzbaer.java.tools.yamahacontrol;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.GridBagConstraints;
import java.awt.Insets;
import java.awt.event.ActionListener;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.Vector;

import javax.swing.JButton;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextField;
import javax.swing.ListCellRenderer;
import javax.swing.ListModel;
import javax.swing.ListSelectionModel;
import javax.swing.event.ListDataEvent;
import javax.swing.event.ListDataListener;

import net.schwarzbaer.gui.Tables.LabelRendererComponent;
import net.schwarzbaer.java.tools.yamahacontrol.YamahaControl.GridBagPanel;
import net.schwarzbaer.java.tools.yamahacontrol.YamahaControl.Log;
import net.schwarzbaer.java.tools.yamahacontrol.YamahaControl.SmallImages;

class LineList2 {

	private Device device;
	private Device.ListInfo listInfo;
	private LineListUser lineListUser;
	private Device.UpdateWish listInfoUpdateWish;
	private Device.UpdateWish playInfoUpdateWish;
	private Vector<JButton> buttons;
	
	private JTextField lineListLabel;
	private JList<Device.ListInfo.Line> lineList;
	private JScrollPane lineListScrollPane;
	
	private LineListModel lineListModel;
	private LineRenderer lineRenderer;
	private boolean ignoreListSelection;

	LineList2(LineListUser lineListUser, Device.UpdateWish listInfoUpdateWish, Device.UpdateWish playInfoUpdateWish) {
		setDeviceAndListInfo(null,null);
		this.lineListUser = lineListUser;
		this.listInfoUpdateWish = listInfoUpdateWish;
		this.playInfoUpdateWish = playInfoUpdateWish;
		this.buttons = new Vector<>();
		
		this.lineListLabel = null;
		this.lineList = null;
		this.lineListScrollPane = null;
		this.lineListModel = null;
		this.lineRenderer = null;
		this.ignoreListSelection = false;
	}

	public void setDeviceAndListInfo(Device device, Device.ListInfo listInfo) {
		this.device = device;
		this.listInfo = listInfo;
	}

	static interface LineListUser {
//		void setEnabledGuiIfPossible(boolean enabled);
		void updatePlayInfo();
	}

	public void setEnabledGUI(boolean enabled) {
		lineListLabel.setEnabled(enabled);
		lineList.setEnabled(enabled);
		buttons.forEach(b->b.setEnabled(enabled));
	}

	public void updateLineList() {
		if (!equals(lineListModel.lines.length,listInfo.maxLine) || !equals(lineListModel.menuName,listInfo.menuName) || !equals(lineListModel.menuLayer,listInfo.menuLayer)) {
			if (listInfo.maxLine==null) lineListModel = new LineListModel();
			else lineListModel = new LineListModel((int)listInfo.maxLine,listInfo.menuName,listInfo.menuLayer);
			lineList.setModel(lineListModel);
			Log.info(getClass(), "change LineListModel: %s", lineListModel);
			// TODO: start data acquisition
		}
		
		lineListLabel.setText(String.format("[Level %s]    %s   %s",
				listInfo.menuLayer,
				(listInfo.menuName==null?"":listInfo.menuName),
				(listInfo.maxLine ==null?"":("("+listInfo.maxLine+")"))));
		
		if (listInfo.currentLine!=null)
			lineListModel.updateData(listInfo.currentLine,listInfo.lines);
	}

	private boolean equals(String str1, String str2) {
		if (str1==null && str2==null) return true;
		if (str1==null || str2==null) return false;
		return str1.equals(str2);
	}

	private boolean equals(Integer i1, Integer i2) {
		if (i1==null && i2==null) return true;
		if (i1==null || i2==null) return false;
		return i1.intValue() == i2.intValue();
	}

	public JPanel createGUIelements() {
		lineListLabel = new JTextField("???");
		lineListLabel.setEditable(false);
		
		ignoreListSelection = false;
		lineRenderer = new LineRenderer();
		lineListModel = new LineListModel();
		lineList = new JList<Device.ListInfo.Line>(lineListModel);
		lineList.setCellRenderer(lineRenderer);
		lineList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
		lineList.addListSelectionListener(e -> {
			if (e.getValueIsAdjusting()) return;
			if (ignoreListSelection) return;
			
//			if (listInfo.menuStatus==Device.Value.ReadyOrBusy.Busy) return;
//			
//			ListInfo.Line line = lineList.getSelectedValue();
//			if (line==null) return;
//			if (line.attr==Device.Value.LineAttribute.Unselectable) return;
//			
//			listInfo.sendDirectSelect(line);
//			device.update(EnumSet.of(listInfoUpdateWish,playInfoUpdateWish));
//			updateLineList();
//			lineListUser.updatePlayInfo();
		});
		
		lineListScrollPane = new JScrollPane(lineList);
		lineListScrollPane.setPreferredSize(new Dimension(500, 200));
		
		buttons.clear();
		GridBagPanel buttonsPanel = new GridBagPanel();
		createButtons(buttonsPanel);
		
		JPanel lineListPanel = new JPanel(new BorderLayout(3,3));
		lineListPanel.add(lineListLabel      ,BorderLayout.NORTH);
		lineListPanel.add(lineListScrollPane ,BorderLayout.CENTER);
		lineListPanel.add(buttonsPanel       ,BorderLayout.SOUTH);
		
		return lineListPanel;
	}

	private void createButtons(GridBagPanel buttonsPanel) {
		buttonsPanel.setInsets(new Insets(0,3,0,3));
		buttonsPanel.add(createButton(Device.Value.CursorSelect.ReturnToHome), 0,0, 0,0, 1,1, GridBagConstraints.BOTH);
		buttonsPanel.add(createButton(Device.Value.CursorSelect.Return      ), 1,0, 0,0, 1,1, GridBagConstraints.BOTH);
		
//		buttonsPanel.add(createButton(ButtonID.Home    ), 0,0, 0,0, 1,1, GridBagConstraints.BOTH);
////		buttonsPanel.add(createButton(ButtonID.Jump    ), 0,1, 0,0, 1,1, GridBagConstraints.BOTH);
//		buttonsPanel.add(createButton(ButtonID.Return  ), 1,0, 0,0, 1,1, GridBagConstraints.BOTH);
////		buttonsPanel.add(createButton(ButtonID.Select  ), 1,1, 0,0, 1,1, GridBagConstraints.BOTH);
////		buttonsPanel.add(createButton(ButtonID.Up      ), 2,0, 0,0, 1,1, GridBagConstraints.BOTH);
////		buttonsPanel.add(createButton(ButtonID.Down    ), 2,1, 0,0, 1,1, GridBagConstraints.BOTH);
////		buttonsPanel.add(createButton(ButtonID.PageUp  ), 3,0, 0,0, 1,1, GridBagConstraints.BOTH);
////		buttonsPanel.add(createButton(ButtonID.PageDown), 3,1, 0,0, 1,1, GridBagConstraints.BOTH);
	}
	
//	private enum ButtonID {
//		/*Up,*/ Home, Return, /*Select, PageUp("Page Up"), Down, PageDown("Page Down"), Jump("Jump to Line")*/;
//
//		String label;
//		ButtonID() { label = toString(); }
//		ButtonID(String label) { this.label = label; }
//		public String getLabel() { return label; }
//	}
	
	private JButton createButton(Device.Value.CursorSelect cursorSelect) {
		JButton button = YamahaControl.createButton(cursorSelect.getLabel(), createCursorSelectListener(cursorSelect), true);
		buttons.add(button);
		return button;
	}
	
//	private JButton createButton(ButtonID buttonID) {
//		JButton button = YamahaControl.createButton(buttonID.getLabel(), createListener(buttonID), true);
//		buttons.add(button);
//		return button;
//	}

//	private ActionListener createListener(ButtonID buttonID) {
//		switch (buttonID) {
////		case Up    : return createCursorSelectListener(Device.Value.CursorSelect.Up);
////		case Down  : return createCursorSelectListener(Device.Value.CursorSelect.Down);
//		case Return: return createCursorSelectListener(Device.Value.CursorSelect.Return);
////		case Select: return createCursorSelectListener(Device.Value.CursorSelect.Sel);
//		case Home  : return createCursorSelectListener(Device.Value.CursorSelect.ReturnToHome);
//		
////		case Jump  : return createJumpToLineListener();
//		
////		case PageUp  : return createPageSelectListener(Device.Value.PageSelect.Up);
////		case PageDown: return createPageSelectListener(Device.Value.PageSelect.Down);
//		}
//		return e->{};
//	}

//	private ActionListener createJumpToLineListener() {
//		return e->{
//			String valurStr = JOptionPane.showInputDialog(lineList, "Enter line number:", listInfo.currentLine);
//			int lineNumber;
//			try { lineNumber = Integer.parseInt(valurStr); }
//			catch (NumberFormatException e1) { return; }
//			listInfo.sendJumpToLine(lineNumber);
//			device.update(EnumSet.of(listInfoUpdateWish));
//			updateLineList();
//		};
//	}

//	private ActionListener createPageSelectListener(Device.Value.PageSelect pageSelect) {
//		return e->{
//			listInfo.sendPageSelect(pageSelect);
//			device.update(EnumSet.of(listInfoUpdateWish));
//			updateLineList();
//		};
//	}

	private ActionListener createCursorSelectListener(Device.Value.CursorSelect cursorSelect) {
		return e->{
			listInfo.sendCursorSelect(cursorSelect);
			device.update(EnumSet.of(listInfoUpdateWish,playInfoUpdateWish));
			updateLineList();
			lineListUser.updatePlayInfo();
		};
	}

	private static class LineListModel implements ListModel<Device.ListInfo.Line> {
		
		public Integer menuLayer;
		public String menuName;
		Vector<ListDataListener> listDataListeners;
		Device.ListInfo.Line[] lines;
		
		public LineListModel() {
			this(0,null,null);
		}

		LineListModel(int numberOfLines, String menuName, Integer menuLayer) {
			this.menuName = menuName;
			this.menuLayer = menuLayer;
			listDataListeners = new Vector<>();
			lines = new Device.ListInfo.Line[numberOfLines];
			Arrays.fill(lines, null);
		}

		@Override
		public String toString() {
			return "LineListModel [ menuLayer=" + menuLayer + ", menuName=" + menuName + ", lines[" + lines.length + "] ]";
		}

		public void updateData(int currentLine, Vector<Device.ListInfo.Line> newLines) {
			int blockStartIndex = ((currentLine-1)&(~0x7));
			newLines.forEach(line->{
				int i = blockStartIndex+line.index-1;
				if (line.index>0 && 0<=i && i<lines.length) {
					lines[i] = line;
				}
			});
			ListDataEvent listDataEvent = new ListDataEvent(LineListModel.this, ListDataEvent.CONTENTS_CHANGED, blockStartIndex, blockStartIndex+8);
			listDataListeners.forEach(listener->listener.contentsChanged(listDataEvent));
		}

		@Override public int getSize() { return lines.length; }
		@Override public Device.ListInfo.Line getElementAt(int index) {
			if (index<0 || index>=lines.length) return null;
			return lines[index];
		}
		
		@Override public void    addListDataListener(ListDataListener l) { listDataListeners.   add(l); }
		@Override public void removeListDataListener(ListDataListener l) { listDataListeners.remove(l); }
	
	}

	private static class LineRenderer implements ListCellRenderer<Device.ListInfo.Line>{
		
		private LabelRendererComponent rendererComponent;
	
		LineRenderer() {
			rendererComponent = new LabelRendererComponent();
			rendererComponent.setPreferredSize(new Dimension(10,20));
		}
	
		@Override
		public Component getListCellRendererComponent(JList<? extends Device.ListInfo.Line> list, Device.ListInfo.Line line, int index, boolean isSelected, boolean cellHasFocus) {
			if (line==null) {
				rendererComponent.setIcon(null);
				rendererComponent.setText("  loading ...");
			} else {
				switch (line.attr) {
				case Container     : rendererComponent.setIcon(YamahaControl.smallImages.get(SmallImages.FolderIcon)); break;
				case Item          : rendererComponent.setIcon(YamahaControl.smallImages.get(SmallImages.IconOn)); break;
				case UnplayableItem: rendererComponent.setIcon(YamahaControl.smallImages.get(SmallImages.IconOff)); break;
				case Unselectable  : rendererComponent.setIcon(null); break;
				}
				rendererComponent.setText(line.txt==null?"":line.txt);
			}
			if (!list.isEnabled()) {
				rendererComponent.setOpaque(false);
				rendererComponent.setForeground(Color.GRAY);
			} else {
				rendererComponent.setOpaque(isSelected);
				rendererComponent.setBackground(isSelected?list.getSelectionBackground():list.getBackground());
				rendererComponent.setForeground(isSelected?list.getSelectionForeground():list.getForeground());
			}
			
			return rendererComponent;
		}
		
	}
}
