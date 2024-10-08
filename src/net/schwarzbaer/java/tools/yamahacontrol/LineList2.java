package net.schwarzbaer.java.tools.yamahacontrol;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.GridBagConstraints;
import java.awt.Insets;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.KeyEvent;
import java.awt.event.KeyListener;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.Vector;

import javax.swing.BorderFactory;
import javax.swing.Icon;
import javax.swing.JButton;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.JScrollBar;
import javax.swing.JScrollPane;
import javax.swing.JTextField;
import javax.swing.ListCellRenderer;
import javax.swing.ListModel;
import javax.swing.ListSelectionModel;
import javax.swing.SwingUtilities;
import javax.swing.border.Border;
import javax.swing.event.ListDataEvent;
import javax.swing.event.ListDataListener;

import net.schwarzbaer.java.lib.gui.FileChooser;
import net.schwarzbaer.java.lib.gui.Tables.LabelRendererComponent;
import net.schwarzbaer.java.tools.yamahacontrol.YamahaControl.FrequentlyTask;
import net.schwarzbaer.java.tools.yamahacontrol.YamahaControl.GridBagPanel;
import net.schwarzbaer.java.tools.yamahacontrol.YamahaControl.SmallImages;
import net.schwarzbaer.java.tools.yamahacontrol.YamahaControl.ToolbarIcons;

class LineList2 {
	
//	private static boolean KeyStrokesWereListed = false;
	private Device device;
	private Device.ListInfo listInfo;
	private LineList2User lineListUser;
	private Device.UpdateWish listInfoUpdateWish;
	private Device.UpdateWish playInfoUpdateWish;
	private Vector<JButton> buttons;
	
	private JTextField lineListLabel;
	private JList<Device.ListInfo.Line> lineList;
	private JScrollPane lineListScrollPane;
	
	private LineList2Model lineListModel;
	private LineRenderer lineRenderer;
	private LineListLoader lineListLoader;
	private FrequentlyTask waitUntilListReady;
	private final FileChooser txtFileChooser;

	LineList2(LineList2User lineListUser, Device.UpdateWish listInfoUpdateWish, Device.UpdateWish playInfoUpdateWish) {
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
		
		this.lineListLoader = null;
		this.lineListLoader = new LineListLoader();
		this.waitUntilListReady = new YamahaControl.FrequentlyTask(200,true,()->{
			device.update(EnumSet.of(listInfoUpdateWish));
			if (listInfo.menuStatus==Device.Value.ReadyOrBusy.Ready)
				waitUntilListReady.stop();
		});
		
		txtFileChooser = new FileChooser("Text-File","txt");
	}

	public void setDeviceAndListInfo(Device device, Device.ListInfo listInfo) {
		this.device = device;
		this.listInfo = listInfo;
	}
	
	private class LineListLoader implements Runnable {
		
		private FrequentlyTask repeater;
		private boolean isInStep;
		
		LineListLoader() {
			repeater = new YamahaControl.FrequentlyTask(100,true,this);
			isInStep = false;
		}
		
		@Override
		public void run() {
			isInStep = true;
			device.update(EnumSet.of(listInfoUpdateWish));
			while (listInfo.menuStatus==Device.Value.ReadyOrBusy.Ready) {
				updateLineList();
				int blockIndex = lineListModel.getNextBlockToLoad();
				if (blockIndex<0) {
					repeater.stop();
					break;
				}
				listInfo.sendJumpToLine(1+blockIndex*8);
				device.update(EnumSet.of(listInfoUpdateWish));
			}
			isInStep = false;
		}

		public void start() {
			if (isInStep) return;
			repeater.start();
		}
	}

	static interface LineList2User {
//		void setEnabledGuiIfPossible(boolean enabled);
		void updatePlayInfo();
	}

	public void setEnabledGUI(boolean enabled) {
		lineListLabel.setEnabled(enabled);
		lineList.setEnabled(enabled);
		buttons.forEach(b->b.setEnabled(enabled));
	}

	public void updateLineList() {
		if (listInfo==null) return;
		
		synchronized (listInfo) {
			if (listInfo.menuStatus!=Device.Value.ReadyOrBusy.Ready) return;
			
			if (!equals(lineListModel.lines.length,listInfo.maxLine) || !equals(lineListModel.menuName,listInfo.menuName) || !equals(lineListModel.menuLayer,listInfo.menuLayer)) {
				final int selectedIndex;
				if (listInfo.maxLine==null) {
					selectedIndex = -1;
					lineListModel = new LineList2Model();
				} else {
					selectedIndex = listInfo.currentLine!=null?listInfo.currentLine-1:-1;
					lineListModel = new LineList2Model(listInfo.maxLine,listInfo.menuName,listInfo.menuLayer);
				}
				lineList.setModel(lineListModel);
				lineList.setSelectedIndex(selectedIndex);
				if (selectedIndex>=0 && lineListModel.lines.length>0) {
					float ratio = selectedIndex/(float)(lineListModel.lines.length-1);
					//Log.info(getClass(), "[New LineListModel]  %d/%d -> %1.3f", selectedIndex, lineListModel.lines.length, ratio);
					SwingUtilities.invokeLater(()->{
						JScrollBar scrollBar = lineListScrollPane.getVerticalScrollBar();
						int min = scrollBar.getMinimum();
						int max = scrollBar.getMaximum();
						int ext = scrollBar.getVisibleAmount();
						int value = Math.round(ratio*(max-min)-ext/2);
						value = Math.max(min, Math.min(value, max));
						scrollBar.setValue(value);
						//Log.info(getClass(), "[New LineListModel]  %d/%d -> %d  | scrollBar: %d..%d(%d)..%d", selectedIndex, lineListModel.lines.length, value, scrollBar.getMinimum(), scrollBar.getValue(), scrollBar.getVisibleAmount(), scrollBar.getMaximum());
					});
				}
				lineListLoader.start();
			}
			
			lineListLabel.setText(String.format("[Level %s]    %s   %s",
					listInfo.menuLayer,
					(listInfo.menuName==null?"":listInfo.menuName),
					(listInfo.maxLine ==null?"":("("+listInfo.maxLine+")"))));
			
			if (listInfo.currentLine!=null)
				lineListModel.updateData(listInfo.currentLine,listInfo.lines);
		}
	}

	private void saveListContentToFile(ActionEvent e) {
		txtFileChooser.suggestFileName(String.format("L%d - %s", lineListModel.menuLayer, lineListModel.menuName));
		
		if (txtFileChooser.showSaveDialog((Component)e.getSource())!=FileChooser.APPROVE_OPTION) return;
		File file = txtFileChooser.getSelectedFile();
		
		try (PrintWriter out = new PrintWriter(new OutputStreamWriter(new FileOutputStream(file), StandardCharsets.UTF_8))) {
			
			out.printf("[Layer %d] %s%n", lineListModel.menuLayer, lineListModel.menuName);
			out.println();
			for (int i=0; i<lineListModel.lines.length; i++) {
				Device.ListInfo.Line line = lineListModel.lines[i];
				String attrStr = "?";
				if (line.attr!=null)
					switch (line.attr) {
					case Container     : attrStr = "F"; break;
					case Item          : attrStr = ">";break;
					case UnplayableItem: attrStr = "X";break;
					case Unselectable  : attrStr = "UnSel";break;
					}
				out.printf("<%02d|%02d> [%s] %s%n", i+1, line.index, attrStr, line.txt);
			}
			
		}
		catch (FileNotFoundException e1) {
			e1.printStackTrace();
		}
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
		
		lineRenderer = new LineRenderer();
		lineListModel = new LineList2Model();
		lineList = new JList<>(lineListModel);
		
//		if (!KeyStrokesWereListed) {
//			showKeyStrokes();
//			KeyStrokesWereListed = true;
//		}
		
		lineList.setCellRenderer(lineRenderer);
		lineList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
		
		lineList.addKeyListener(new KeyListener() {
			@Override public void keyPressed (KeyEvent e) {}
			@Override public void keyTyped   (KeyEvent e) {}
			@Override public void keyReleased(KeyEvent e) {
				switch (e.getKeyCode()) {
				case KeyEvent.VK_ENTER: selectSelectedIndex(); break;
				case KeyEvent.VK_SPACE: selectSelectedIndex(); break;
				case KeyEvent.VK_BACK_SPACE: sendCursorSelect(Device.Value.CursorSelect.Return,true); break;
				}
			}
		});
		lineList.addMouseListener(new MouseAdapter() {
			@Override public void mouseClicked(MouseEvent e) {
				selectSelectedIndex();
			}
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

//	private void showKeyStrokes() {
//		InputMap inputMap = lineList.getInputMap();
//		KeyStroke[] keyStrokes = inputMap.allKeys();
//		KeyStroke[] keyStrokes1 = inputMap.keys();
//		//Arrays.sort(keyStrokes,Comparator.nullsFirst(Comparator.comparing(key->key.toString())));
//		Arrays.sort(keyStrokes,Comparator.nullsFirst(Comparator.comparing(key->inputMap.get(key),Comparator.nullsFirst(Comparator.comparing(obj->obj.toString())))));
//		for (KeyStroke key:keyStrokes) {
//			Object object = inputMap.get(key);
//			System.out.printf("key[%-30s] %9s-> %s%n", key, isIn(key,keyStrokes1)?"":"[parent] ", object);
//		}
//	}

//	private boolean isIn(KeyStroke key1, KeyStroke[] keyStrokes) {
//		if (keyStrokes==null) return false;
//		if (key1==null) return false;
//		for (KeyStroke key:keyStrokes)
//			if (key1.equals(key)) return true;
//		return false;
//	}

	private void selectSelectedIndex() {
		int index = lineList.getSelectedIndex();
		if (index<0) return;
		lineList.setSelectedIndex(-1);
		
		Device.ListInfo.Line line = lineListModel.getElementAt(index);
		if (line==null || line.attr==null) return;
		
		switch(line.attr) {
		
		case Container:
			lineListLabel.setText("loading ...");			
			lineList.setModel(lineListModel = new LineList2Model());
			
			listInfo.sendJumpToLine(index+1);
			//waitUntilListReady.start();
			//listInfo.sendDirectSelect((index&0x7)+1);
			listInfo.sendCursorSelect(Device.Value.CursorSelect.Sel);
			break;
			
		case UnplayableItem:
		case Unselectable: // return;
			
		case Item:
			listInfo.sendJumpToLine(index+1);
			//waitUntilListReady.start();
			//listInfo.sendDirectSelect((index&0x7)+1);
			listInfo.sendCursorSelect(Device.Value.CursorSelect.Sel);
			break;
		}
		
		device.update(EnumSet.of(listInfoUpdateWish,playInfoUpdateWish));
		updateLineList();
		lineListUser.updatePlayInfo();
	}

	private void createButtons(GridBagPanel buttonsPanel) {
		buttonsPanel.setInsets(new Insets(0,3,0,3));
		buttonsPanel.add(createButton(Device.Value.CursorSelect.ReturnToHome, true), 0,0, 0,0, 1,1, GridBagConstraints.BOTH);
		buttonsPanel.add(createButton(Device.Value.CursorSelect.Return      , true), 1,0, 0,0, 1,1, GridBagConstraints.BOTH);
		buttonsPanel.add(createButton("Save List", ToolbarIcons.Save.getIcon(), true, e->saveListContentToFile(e)), 2,0, 0,0, 1,1, GridBagConstraints.BOTH);
	}
	
	private JButton createButton(String title, Icon icon, boolean isEnabled, ActionListener al) {
		JButton button = YamahaControl.createButton(title, icon, isEnabled, al);
		buttons.add(button);
		return button;
	}
	
	private JButton createButton(Device.Value.CursorSelect cursorSelect, boolean listReset) {
		JButton button = YamahaControl.createButton(cursorSelect.getLabel(), true, e->sendCursorSelect(cursorSelect, listReset));
		buttons.add(button);
		return button;
	}

	private void sendCursorSelect(Device.Value.CursorSelect cursorSelect, boolean listReset) {
		listInfo.sendCursorSelect(cursorSelect);
		if (listReset) {
			lineListLabel.setText("loading ...");			
			lineList.setModel(lineListModel = new LineList2Model());
			lineList.setSelectedIndex(-1);
		}
		device.update(EnumSet.of(listInfoUpdateWish,playInfoUpdateWish));
		updateLineList();
		lineListUser.updatePlayInfo();
	}

	private static class LineList2Model implements ListModel<Device.ListInfo.Line> {
		
		public Integer menuLayer;
		public String menuName;
		Vector<ListDataListener> listDataListeners;
		Device.ListInfo.Line[] lines;
		
		public LineList2Model() {
			this(0,null,null);
		}

		LineList2Model(int numberOfLines, String menuName, Integer menuLayer) {
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

		public int getNextBlockToLoad() {
			for (int i=0; i<lines.length; ++i)
				if (lines[i]==null)
					return i>>3;
			return -1;
		}

		public void updateData(int currentLine, Vector<Device.ListInfo.Line> newLines) {
			int blockStartLineIndex = ((currentLine-1)&(~0x7));
			newLines.forEach(line->{
				int i = blockStartLineIndex+line.index-1;
				if (line.index>0 && 0<=i && i<lines.length) {
					lines[i] = line;
				}
			});
//			if (lines.length>0)
//				Log.info(getClass(), "data updated: %d..%d", blockStartLineIndex, blockStartLineIndex+8);
			ListDataEvent listDataEvent = new ListDataEvent(LineList2Model.this, ListDataEvent.CONTENTS_CHANGED, blockStartLineIndex, blockStartLineIndex+8);
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
		private Border focusBorder;
		private Border emptyBorder;
	
		LineRenderer() {
			rendererComponent = new LabelRendererComponent();
			rendererComponent.setPreferredSize(new Dimension(10,20));
			emptyBorder = BorderFactory.createEmptyBorder(1, 1, 1, 1);
			focusBorder = BorderFactory.createDashedBorder(Color.DARK_GRAY);
		}

		@Override
		public Component getListCellRendererComponent(JList<? extends Device.ListInfo.Line> list, Device.ListInfo.Line line, int index, boolean isSelected, boolean cellHasFocus) {
			
			rendererComponent.setEnabled(list.isEnabled());
			
			if (line==null) {
				rendererComponent.setIcon(null);
				rendererComponent.setText("  loading ...");
				rendererComponent.setEnabled(false);
			} else {
				switch (line.attr) {
				case Container     : rendererComponent.setIcon(YamahaControl.smallImages.get(SmallImages.FolderIcon)); break;
				case Item          : rendererComponent.setIcon(YamahaControl.smallImages.get(SmallImages.IconPlay  )); break;
				case UnplayableItem: rendererComponent.setIcon(YamahaControl.smallImages.get(SmallImages.IconNoPlay)); break;
				case Unselectable  : rendererComponent.setIcon(null); rendererComponent.setEnabled(false); break;
				}
				rendererComponent.setText(line.txt==null?"":line.txt);
			}
			rendererComponent.setOpaque(true);
			rendererComponent.setBackground(isSelected?list.getSelectionBackground():list.getBackground());
			rendererComponent.setForeground(isSelected?list.getSelectionForeground():list.getForeground());
			
			rendererComponent.setBorder(cellHasFocus?focusBorder:emptyBorder);
			
			return rendererComponent;
		}
		
	}
}
