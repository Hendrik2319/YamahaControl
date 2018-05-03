package net.schwarzbaer.java.tools.yamahacontrol;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.GridBagConstraints;
import java.awt.Insets;
import java.awt.event.ActionListener;
import java.util.EnumSet;
import java.util.Vector;

import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextField;
import javax.swing.ListCellRenderer;
import javax.swing.ListSelectionModel;

import net.schwarzbaer.gui.Tables;
import net.schwarzbaer.gui.Tables.LabelRendererComponent;
import net.schwarzbaer.java.tools.yamahacontrol.Device.ListInfo;
import net.schwarzbaer.java.tools.yamahacontrol.Device.UpdateWish;
import net.schwarzbaer.java.tools.yamahacontrol.YamahaControl.FrequentlyTask;
import net.schwarzbaer.java.tools.yamahacontrol.YamahaControl.GridBagPanel;
import net.schwarzbaer.java.tools.yamahacontrol.YamahaControl.SmallImages;

class LineList {
	
	private JTextField lineListLabel;
	private JList<ListInfo.Line> lineList;
	private JScrollPane lineListScrollPane;
	private Vector<JButton> buttons;
	
	private boolean ignoreListSelection;
	
	private LineList.LineRenderer lineRenderer;
	private FrequentlyTask lineListUpdater;
	
	private Device device;
	private ListInfo listInfo;
	
	private LineList.LineListUser lineListUser;
	private UpdateWish listInfoUpdateWish;
	private UpdateWish playInfoUpdateWish;
	
	LineList(LineList.LineListUser lineListUser, UpdateWish listInfoUpdateWish, UpdateWish playInfoUpdateWish) {
		this.device = null;
		this.listInfo = null;
		this.lineListUser = lineListUser;
		this.listInfoUpdateWish = listInfoUpdateWish;
		this.playInfoUpdateWish = playInfoUpdateWish;
		this.buttons = new Vector<>();
		
		lineListUpdater = new FrequentlyTask(200,()->{
			device.update(EnumSet.of(listInfoUpdateWish));
			updateLineList();
			if (listInfo!=null && listInfo.menuStatus==Device.Value.ReadyOrBusy.Ready)
				lineListUpdater.stop();
		});
	}

	public void setDeviceAndListInfo(Device device, ListInfo listInfo) {
		this.device = device;
		this.listInfo = listInfo;
	}
	
	public void setEnabled(boolean enabled) {
		lineList.setEnabled(enabled);
		buttons.forEach(b->b.setEnabled(enabled));
	}

	static interface LineListUser {
		void setEnabled(boolean enabled);
		void updatePlayInfo();
	}
	

	void updateLineList() {
		lineListUser.setEnabled(listInfo.menuStatus==Device.Value.ReadyOrBusy.Ready);
		//System.out.println("updateLineList() -> listInfo.menuStatus: "+listInfo.menuStatus);
		
		String lineListLabelStr = String.format("[%s] %s", listInfo.menuLayer, listInfo.menuName==null?"":listInfo.menuName);
		if (listInfo.currentLine==null || listInfo.maxLine==null || listInfo.currentLine<listInfo.maxLine)
			lineListLabelStr += "  "+listInfo.currentLine+"/"+listInfo.maxLine;
		lineListLabel.setText(lineListLabelStr);
		lineList.setListData(listInfo.lines);
		
		if (listInfo.currentLine!=null) {
			int lineIndex = ((listInfo.currentLine-1)&0x7);
			//ignoreListSelection=true;
			//lineList.setSelectedIndex(lineIndex);
			//ignoreListSelection=false;
			lineRenderer.setSelected(lineIndex+1);
		}
		
		if (listInfo.menuStatus!=Device.Value.ReadyOrBusy.Ready && !lineListUpdater.isRunning()) {
			//System.out.println("updateLineList() -> start lineListUpdater");
			lineListUpdater.start();
		}
	}
	
	public JPanel createGUIelements() {
		lineListLabel = new JTextField("???");
		lineListLabel.setEditable(false);
		
		ignoreListSelection = false;
		lineRenderer = new LineRenderer();
		lineList = new JList<ListInfo.Line>();
		lineList.setCellRenderer(lineRenderer);
		lineList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
		lineList.addListSelectionListener(e -> {
			if (e.getValueIsAdjusting()) return;
			if (ignoreListSelection) return;
			if (listInfo.menuStatus==Device.Value.ReadyOrBusy.Busy) return;
			
			ListInfo.Line line = lineList.getSelectedValue();
			if (line==null) return;
			if (line.attr==Device.Value.LineAttribute.Unselectable) return;
			
			listInfo.sendDirectSelect(line);
			device.update(EnumSet.of(listInfoUpdateWish,playInfoUpdateWish));
			updateLineList();
			lineListUser.updatePlayInfo();
		});
		
		lineListScrollPane = new JScrollPane(lineList);
		lineListScrollPane.setPreferredSize(new Dimension(500, 200));
		
		buttons.clear();
		GridBagPanel buttonsPanel = new GridBagPanel();
		buttonsPanel.setInsets(new Insets(3,3,3,3));
		buttonsPanel.add(new JLabel(""), 0,0, 1,1, 1,3, GridBagConstraints.BOTH);
		buttonsPanel.add(new JLabel(""), 5,0, 1,1, 1,3, GridBagConstraints.BOTH);
		buttonsPanel.add(createButton(ButtonID.Up      ), 2,0, 0,1, 1,1, GridBagConstraints.BOTH);
		buttonsPanel.add(createButton(ButtonID.Home    ), 4,0, 0,1, 1,1, GridBagConstraints.BOTH);
		buttonsPanel.add(createButton(ButtonID.Return  ), 1,1, 0,1, 1,1, GridBagConstraints.BOTH);
		buttonsPanel.add(createButton(ButtonID.Select  ), 3,1, 0,1, 1,1, GridBagConstraints.BOTH);
		buttonsPanel.add(createButton(ButtonID.PageUp  ), 4,1, 0,1, 1,1, GridBagConstraints.BOTH);
		buttonsPanel.add(createButton(ButtonID.Down    ), 2,2, 0,1, 1,1, GridBagConstraints.BOTH);
		buttonsPanel.add(createButton(ButtonID.PageDown), 4,2, 0,1, 1,1, GridBagConstraints.BOTH);
		
		JPanel lineListPanel = new JPanel(new BorderLayout(3,3));
		lineListPanel.add(lineListLabel      ,BorderLayout.NORTH);
		lineListPanel.add(lineListScrollPane ,BorderLayout.CENTER);
		lineListPanel.add(buttonsPanel       ,BorderLayout.SOUTH);
		
		return lineListPanel;
	}
	
	enum ButtonID {
		Up, Home, Return, Select, PageUp("Page Up"), Down, PageDown("Page Down");

		String label;
		ButtonID() { label = toString(); }
		ButtonID(String label) { this.label = label; }
		public String getLabel() { return label; }
	}
	
	private JButton createButton(LineList.ButtonID buttonID) {
		JButton button = YamahaControl.createButton(buttonID.getLabel(), createListener(buttonID), true);
		buttons.add(button);
		return button;
	}

	private ActionListener createListener(LineList.ButtonID buttonID) {
		switch (buttonID) {
		case Up    : return createCursorSelectListener(Device.Value.CursorSelect.Up);
		case Down  : return createCursorSelectListener(Device.Value.CursorSelect.Down);
		case Return: return createCursorSelectListener(Device.Value.CursorSelect.Return);
		case Select: return createCursorSelectListener(Device.Value.CursorSelect.Sel);
		case Home  : return createCursorSelectListener(Device.Value.CursorSelect.ReturnToHome);
			
		case PageUp  : return createPageSelectListener(Device.Value.PageSelect.Up);
		case PageDown: return createPageSelectListener(Device.Value.PageSelect.Down);
		}
		return e->{};
	}

	private ActionListener createPageSelectListener(Device.Value.PageSelect pageSelect) {
		return e->{
			listInfo.sendPageSelect(pageSelect);
			device.update(EnumSet.of(listInfoUpdateWish));
			updateLineList();
		};
	}

	private ActionListener createCursorSelectListener(Device.Value.CursorSelect cursorSelect) {
		return e->{
			listInfo.sendCursorSelect(cursorSelect);
			device.update(EnumSet.of(listInfoUpdateWish,playInfoUpdateWish));
			updateLineList();
			lineListUser.updatePlayInfo();
		};
	}

	private static class LineRenderer implements ListCellRenderer<ListInfo.Line>{
		
		private LabelRendererComponent rendererComponent;
		private int selectedLineIndex;
	
		LineRenderer() {
			rendererComponent = new Tables.LabelRendererComponent();
			rendererComponent.setPreferredSize(new Dimension(10,20));
			this.selectedLineIndex = -1;
		}
	
		public void setSelected(int selectedLineIndex) {
			this.selectedLineIndex = selectedLineIndex;
		}
	
		@Override
		public Component getListCellRendererComponent(JList<? extends ListInfo.Line> list, ListInfo.Line line, int index, boolean isSelected, boolean cellHasFocus) {
			switch (line.attr) {
			case Container     : rendererComponent.setIcon(YamahaControl.smallImages.get(SmallImages.FolderIcon)); break;
			case Item          : rendererComponent.setIcon(YamahaControl.smallImages.get(SmallImages.IconOn)); break;
			case UnplayableItem: rendererComponent.setIcon(YamahaControl.smallImages.get(SmallImages.IconOff)); break;
			case Unselectable  : rendererComponent.setIcon(null); break;
			}
			rendererComponent.setText(line.txt==null?"":line.txt);
			if (!list.isEnabled()) {
				rendererComponent.setOpaque(false);
				rendererComponent.setForeground(Color.GRAY);
			} else {
				rendererComponent.setOpaque(isSelected || line.index==selectedLineIndex);
				rendererComponent.setBackground(isSelected?list.getSelectionBackground():line.index==selectedLineIndex?Color.GRAY :list.getBackground());
				rendererComponent.setForeground(isSelected?list.getSelectionForeground():line.index==selectedLineIndex?Color.WHITE:list.getForeground());
			}
			
			return rendererComponent;
		}
		
	}
}