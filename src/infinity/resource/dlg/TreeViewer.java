// Near Infinity - An Infinity Engine Browser and Editor
// Copyright (C) 2001 - 2005 Jon Olav Hauglid
// See LICENSE.txt for license information

package infinity.resource.dlg;

import infinity.NearInfinity;
import infinity.datatype.Flag;
import infinity.datatype.ResourceRef;
import infinity.datatype.SectionCount;
import infinity.datatype.StringRef;
import infinity.gui.BrowserMenuBar;
import infinity.gui.ViewerUtil;
import infinity.icon.Icons;
import infinity.resource.ResourceFactory;
import infinity.resource.StructEntry;
import infinity.util.StringResource;

import java.awt.BorderLayout;
import java.awt.CardLayout;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Stack;

import javax.swing.BorderFactory;
import javax.swing.Icon;
import javax.swing.ImageIcon;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSplitPane;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.JTree;
import javax.swing.JViewport;
import javax.swing.SwingUtilities;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;
import javax.swing.event.TableModelEvent;
import javax.swing.event.TableModelListener;
import javax.swing.event.TreeExpansionEvent;
import javax.swing.event.TreeModelEvent;
import javax.swing.event.TreeModelListener;
import javax.swing.event.TreeSelectionEvent;
import javax.swing.event.TreeSelectionListener;
import javax.swing.event.TreeWillExpandListener;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeCellRenderer;
import javax.swing.tree.ExpandVetoException;
import javax.swing.tree.TreeModel;
import javax.swing.tree.TreeNode;
import javax.swing.tree.TreePath;


/** Show dialog content as tree structure. */
final class TreeViewer extends JPanel implements TreeSelectionListener, TableModelListener
{
  private final DlgResource dlg;
  private final DlgTreeModel dlgModel;
  private final JTree dlgTree;
  private final ItemInfo dlgInfo;

  private JScrollPane spInfo, spTree;

  TreeViewer(DlgResource dlg)
  {
    super(new BorderLayout());
    this.dlg = dlg;
    this.dlg.addTableModelListener(this);
    dlgModel = new DlgTreeModel(this.dlg);
    dlgTree = new JTree();
    dlgTree.addTreeSelectionListener(this);
    dlgInfo = new ItemInfo();
    initControls();
  }

//--------------------- Begin Interface TreeSelectionListener ---------------------

  @Override
  public void valueChanged(TreeSelectionEvent e)
  {
    if (e.getSource() == dlgTree) {
      Object node = dlgTree.getLastSelectedPathComponent();
      if (node instanceof DefaultMutableTreeNode) {
        Object data = ((DefaultMutableTreeNode)node).getUserObject();
        if (data instanceof StateItem) {
          // dialog state found
          updateStateInfo((StateItem)data);
        } else if (data instanceof TransitionItem) {
          // dialog response found
          updateTransitionInfo((TransitionItem)data);
        } else {
          // no valid type found
          dlgInfo.showPanel(ItemInfo.CARD_EMPTY);
        }
      } else {
        // no node selected
        dlgInfo.showPanel(ItemInfo.CARD_EMPTY);
      }
    }
  }

//--------------------- End Interface TreeSelectionListener ---------------------

//--------------------- Begin Interface TableModelListener ---------------------

 @Override
 public void tableChanged(TableModelEvent e)
 {
   // Insertion or removal of nodes not yet supported
   if (e.getType() == TableModelEvent.UPDATE) {
     if (e.getSource() instanceof State) {
       State state = (State)e.getSource();
       dlgModel.updateState(state);
     } else if (e.getSource() instanceof Transition) {
       Transition trans = (Transition)e.getSource();
       dlgModel.updateTransition(trans);
     } else if (e.getSource() instanceof DlgResource) {
       dlgModel.updateRoot();
     }
   }
 }

//--------------------- End Interface TableModelListener ---------------------

  /** Jumps to the first available node containing the specified structure. */
  public void showStateWithStructEntry(StructEntry entry)
  {
    DefaultMutableTreeNode node = null;
    if (entry instanceof State) {
      int stateIdx = ((State)entry).getNumber();
      node = dlgModel.findStateNode(stateIdx);
    } else if (entry instanceof Transition) {
      int transIdx = ((Transition)entry).getNumber();
      node = dlgModel.findTransitionNode(transIdx);
    } else if (entry instanceof StateTrigger) {
      int triggerIdx = ((StateTrigger)entry).getNumber();
      node = dlgModel.findStateTriggerNode(triggerIdx);
    } else if (entry instanceof ResponseTrigger) {
      int triggerIdx = ((ResponseTrigger)entry).getNumber();
      node = dlgModel.findResponseTriggerNode(triggerIdx);
    } else if (entry instanceof Action) {
      int actionIdx = ((Action)entry).getNumber();
      node = dlgModel.findActionNode(actionIdx);
    } else if (entry instanceof StringRef) {
      // may happen when using the DLG Search
      node = dlgModel.findStringRefNode(((StringRef)entry).getValue());
    }

    // selecting node in tree view
    if (node != null) {
      dlgTree.setSelectionPath(dlgModel.getTreePath(node));
    }
  }


  private void updateStateInfo(StateItem si)
  {
    if (si != null && si.getDialog() != null && si.getState() != null) {
      DlgResource curDlg = si.getDialog();
      State state = si.getState();
      boolean showStrrefs = BrowserMenuBar.getInstance().showStrrefs();

      // updating info box title
      StringBuilder sb = new StringBuilder(state.getName() + ", ");
      if (curDlg != dlg) {
        sb.append(String.format("Dialog: %1$s, ", curDlg.getResourceEntry().getResourceName()));
      }
      sb.append(String.format("Responses: %1$d", state.getTransCount()));
      if (state.getTriggerIndex() >= 0) {
        sb.append(String.format(", Weight: %1$d", state.getTriggerIndex()));
      }
      dlgInfo.updateControlBorder(ItemInfo.Type.STATE, sb.toString());

      // updating state text
      dlgInfo.showControl(ItemInfo.Type.STATE_TEXT, true);
      dlgInfo.updateControlText(ItemInfo.Type.STATE_TEXT,
                                StringResource.getStringRef(state.getResponse().getValue(),
                                                            showStrrefs));

      // updating state triggers
      if (state.getTriggerIndex() >= 0) {
        dlgInfo.showControl(ItemInfo.Type.STATE_TRIGGER, true);
        StructEntry entry = curDlg.getAttribute(String.format(StateTrigger.FMT_NAME, state.getTriggerIndex()));
        if (entry instanceof StateTrigger) {
          dlgInfo.updateControlText(ItemInfo.Type.STATE_TRIGGER, ((StateTrigger)entry).toString());
        } else {
          dlgInfo.updateControlText(ItemInfo.Type.STATE_TRIGGER, "");
        }
      } else {
        dlgInfo.showControl(ItemInfo.Type.STATE_TRIGGER, false);
      }

      dlgInfo.showPanel(ItemInfo.CARD_STATE);

      // jumping to top of scroll area
      SwingUtilities.invokeLater(new Runnable() {
        @Override
        public void run() { spInfo.getVerticalScrollBar().setValue(0); }
      });
    } else {
      dlgInfo.showPanel(ItemInfo.CARD_EMPTY);
    }
  }

  private void updateTransitionInfo(TransitionItem ti)
  {
    if (ti != null && ti.getDialog() != null && ti.getTransition() != null) {
      DlgResource curDlg = ti.getDialog();
      Transition trans = ti.getTransition();
      boolean showStrrefs = BrowserMenuBar.getInstance().showStrrefs();
      StructEntry entry;

      // updating info box title
      StringBuilder sb = new StringBuilder(trans.getName());
      if (curDlg != dlg) {
        sb.append(String.format(", Dialog: %1$s", curDlg.getResourceEntry().getResourceName()));
      }
      dlgInfo.updateControlBorder(ItemInfo.Type.RESPONSE, sb.toString());

      // updating flags
      dlgInfo.showControl(ItemInfo.Type.RESPONSE_FLAGS, true);
      dlgInfo.updateControlText(ItemInfo.Type.RESPONSE_FLAGS, trans.getFlag().toString());

      // updating response text
      if (trans.getFlag().isFlagSet(0)) {
        dlgInfo.showControl(ItemInfo.Type.RESPONSE_TEXT, true);
        dlgInfo.updateControlText(ItemInfo.Type.RESPONSE_TEXT,
                                  StringResource.getStringRef(trans.getAssociatedText().getValue(),
                                                              showStrrefs));
      } else {
        dlgInfo.showControl(ItemInfo.Type.RESPONSE_TEXT, false);
      }

      // updating journal entry
      if (trans.getFlag().isFlagSet(4)) {
        dlgInfo.showControl(ItemInfo.Type.RESPONSE_JOURNAL, true);
        dlgInfo.updateControlText(ItemInfo.Type.RESPONSE_JOURNAL,
                                  StringResource.getStringRef(trans.getJournalEntry().getValue(),
                                                              showStrrefs));
      } else {
        dlgInfo.showControl(ItemInfo.Type.RESPONSE_JOURNAL, false);
      }

      // updating response trigger
      if (trans.getFlag().isFlagSet(1)) {
        dlgInfo.showControl(ItemInfo.Type.RESPONSE_TRIGGER, true);
        entry = curDlg.getAttribute(String.format(ResponseTrigger.FMT_NAME, trans.getTriggerIndex()));
        if (entry instanceof ResponseTrigger) {
          dlgInfo.updateControlText(ItemInfo.Type.RESPONSE_TRIGGER, ((ResponseTrigger)entry).toString());
        } else {
          dlgInfo.updateControlText(ItemInfo.Type.RESPONSE_TRIGGER, "");
        }
      } else {
        dlgInfo.showControl(ItemInfo.Type.RESPONSE_TRIGGER, false);
      }

      // updating action
      if (trans.getFlag().isFlagSet(2)) {
        dlgInfo.showControl(ItemInfo.Type.RESPONSE_ACTION, true);
        entry = curDlg.getAttribute(String.format(Action.FMT_NAME, trans.getActionIndex()));
        if (entry instanceof Action) {
          dlgInfo.updateControlText(ItemInfo.Type.RESPONSE_ACTION, ((Action)entry).toString());
        } else {
          dlgInfo.updateControlText(ItemInfo.Type.RESPONSE_ACTION, "");
        }
      } else {
        dlgInfo.showControl(ItemInfo.Type.RESPONSE_ACTION, false);
      }

      dlgInfo.showPanel(ItemInfo.CARD_RESPONSE);

      // jumping to top of scroll area
      SwingUtilities.invokeLater(new Runnable() {
        @Override
        public void run() { spInfo.getVerticalScrollBar().setValue(0); }
      });
    } else {
      dlgInfo.showPanel(ItemInfo.CARD_EMPTY);
    }
  }

  private void initControls()
  {
    // initializing info component
    spInfo = new JScrollPane(dlgInfo, JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED,
                             JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
    spInfo.getViewport().addChangeListener(new ChangeListener() {
      @Override
      public void stateChanged(ChangeEvent e)
      {
        // never scroll horizontally
        JViewport vp = (JViewport)e.getSource();
        if (vp != null) {
          Dimension d = vp.getExtentSize();
          if (d.width != vp.getView().getWidth()) {
            d.height = vp.getView().getHeight();
            vp.getView().setSize(d);
          }
        }
      }
    });
    spInfo.getVerticalScrollBar().setUnitIncrement(16);

    // initializing tree component
    JPanel pTree = new JPanel(new GridBagLayout());
    pTree.setBackground(dlgTree.getBackground());
    GridBagConstraints gbc = new GridBagConstraints();
    gbc = ViewerUtil.setGBC(gbc, 0, 0, 1, 1, 1.0, 1.0, GridBagConstraints.FIRST_LINE_START,
                            GridBagConstraints.BOTH, new Insets(4, 4, 4, 4), 0, 0);
    pTree.add(dlgTree, gbc);
    spTree = new JScrollPane(pTree);
    spTree.setBorder(BorderFactory.createEmptyBorder());
    spTree.getHorizontalScrollBar().setUnitIncrement(16);
    spTree.getVerticalScrollBar().setUnitIncrement(16);

    dlgTree.setRootVisible(true);
    dlgTree.setEditable(false);
    DefaultTreeCellRenderer tcr = (DefaultTreeCellRenderer)dlgTree.getCellRenderer();
    tcr.setLeafIcon(null);
    tcr.setOpenIcon(null);
    tcr.setClosedIcon(null);

    // drawing custom icons for each node type
    dlgTree.setCellRenderer(new DefaultTreeCellRenderer() {
      @Override
      public Component getTreeCellRendererComponent(JTree tree, Object value, boolean sel,
                                                    boolean expanded, boolean leaf, int row,
                                                    boolean focused)
      {
        Component c = super.getTreeCellRendererComponent(tree, value, sel, expanded, leaf, row, focused);
        if (value instanceof DefaultMutableTreeNode) {
          Object data = ((DefaultMutableTreeNode)value).getUserObject();
          if (data instanceof ItemBase) {
            setIcon(((ItemBase)data).getIcon());
          } else {
            setIcon(null);
          }
        }
        return c;
      }
    });

    // preventing root node from collapsing
    dlgTree.addTreeWillExpandListener(new TreeWillExpandListener() {
      @Override
      public void treeWillExpand(TreeExpansionEvent event) throws ExpandVetoException
      {
      }

      @Override
      public void treeWillCollapse(TreeExpansionEvent event) throws ExpandVetoException
      {
        if (event.getPath().getLastPathComponent() == dlgModel.getRoot()) {
          throw new ExpandVetoException(event);
        }
      }
    });

    // setting model AFTER customizing visual appearance of the tree control
    dlgTree.setModel(dlgModel);

    // putting components together
    JSplitPane splitv = new JSplitPane(JSplitPane.VERTICAL_SPLIT, spTree, spInfo);
    splitv.setDividerLocation(2 * NearInfinity.getInstance().getContentPane().getHeight() / 5);
    add(splitv, BorderLayout.CENTER);
  }


//-------------------------- INNER CLASSES --------------------------

  // Common base class for node type specific classes
  private static abstract class ItemBase
  {
     private final DlgResource dlg;

     public ItemBase(DlgResource dlg)
     {
       this.dlg = dlg;
     }

     /** Returns the dialog resource object. */
     public DlgResource getDialog()
     {
       return dlg;
     }

     /** Returns the dialog resource name. */
     public String getDialogName()
     {
       if (dlg != null) {
         return dlg.getResourceEntry().getResourceName();
       } else {
         return "";
       }
     }

     /** Returns the icon associated with the item type. */
     public abstract Icon getIcon();
  }

  // Meta class for identifying root node
  private static final class RootItem extends ItemBase
  {
    private static final ImageIcon ICON = Icons.getIcon("RowInsertAfter16.gif");

    private final ArrayList<StateItem> states = new ArrayList<StateItem>();

    private int numStates, numTransitions, numStateTriggers, numResponseTriggers, numActions;
    private String flags;

    public RootItem(DlgResource dlg)
    {
      super(dlg);

      if (getDialog() != null) {
        StructEntry entry = getDialog().getAttribute("# states");
        if (entry instanceof SectionCount) {
          numStates = ((SectionCount)entry).getValue();
        }
        entry = getDialog().getAttribute("# responses");
        if (entry instanceof SectionCount) {
          numTransitions = ((SectionCount)entry).getValue();
        }
        entry = getDialog().getAttribute("# state triggers");
        if (entry instanceof SectionCount) {
          numStateTriggers = ((SectionCount)entry).getValue();
        }
        entry = getDialog().getAttribute("# response triggers");
        if (entry instanceof SectionCount) {
          numResponseTriggers = ((SectionCount)entry).getValue();
        }
        entry = getDialog().getAttribute("# actions");
        if (entry instanceof SectionCount) {
          numActions = ((SectionCount)entry).getValue();
        }
        entry = getDialog().getAttribute("Threat response");
        if (entry instanceof Flag) {
          flags = ((Flag)entry).toString();
        }

        // finding and storing initial states (sorted by trigger index in ascending order)
        for (int i = 0; i < numStates; i++) {
          entry = getDialog().getAttribute(String.format(State.FMT_NAME, i));
          if (entry instanceof State) {
            int triggerIndex = ((State)entry).getTriggerIndex();
            if (triggerIndex >= 0) {
              int j = 0;
              for (; j < states.size(); j++) {
                if (states.get(j).getState().getTriggerIndex() > triggerIndex) {
                  break;
                }
              }
              states.add(j, new StateItem(getDialog(), (State)entry));
            }
          }
        }
      }
    }

    /** Returns number of available initial states. */
    public int getInitialStateCount()
    {
      return states.size();
    }

    /** Returns the StateItem at the given index or null on error. */
    public StateItem getInitialState(int index)
    {
      if (index >= 0 && index < states.size()) {
        return states.get(index);
      }
      return null;
    }

    @Override
    public Icon getIcon()
    {
      return ICON;
    }

    @Override
    public String toString()
    {
      StringBuilder sb = new StringBuilder();
      if (!getDialogName().isEmpty()) {
        sb.append(getDialogName());
      } else {
        sb.append("(Invalid DLG resource)");
      }
      sb.append(" (states: ").append(Integer.toString(numStates));
      sb.append(", responses: ").append(Integer.toString(numTransitions));
      sb.append(", state triggers: ").append(Integer.toString(numStateTriggers));
      sb.append(", response triggers: ").append(Integer.toString(numResponseTriggers));
      sb.append(", actions: ").append(Integer.toString(numActions));
      if (flags != null) {
        sb.append(", flags: ").append(flags);
      }
      sb.append(")");

      return sb.toString();
    }
  }


  // Encapsulates a dialog state entry
  private static final class StateItem extends ItemBase
  {
    private static final ImageIcon ICON = Icons.getIcon("Stop16.gif");
    private static final int MAX_LENGTH = 100;    // max. string length to display

    private State state;

    public StateItem(DlgResource dlg, State state)
    {
      super(dlg);
      this.state = state;
    }

    public State getState()
    {
      return state;
    }

    public void setState(State state)
    {
      if (state != null) {
        this.state = state;
      }
    }

    @Override
    public Icon getIcon()
    {
      return ICON;
    }

    @Override
    public String toString()
    {
      if (state != null) {
        String text = StringResource.getStringRef(state.getResponse().getValue());
        if (text.length() > MAX_LENGTH) {
          text = text.substring(0, MAX_LENGTH) + "...";
        }
        return String.format("%1$s: %2$s", state.getName(), text);
      } else {
        return "(Invalid state)";
      }
    }
  }


  // Encapsulates a dialog transition entry
  private static final class TransitionItem extends ItemBase
  {
    private static final ImageIcon ICON = Icons.getIcon("Play16.gif");
    private static final int MAX_LENGTH = 100;    // max. string length to display

    private Transition trans;

    public TransitionItem(DlgResource dlg, Transition trans)
    {
      super(dlg);
      this.trans = trans;
    }

    public Transition getTransition()
    {
      return trans;
    }

    public void setTransition(Transition trans)
    {
      if (trans != null) {
        this.trans = trans;
      }
    }

    @Override
    public Icon getIcon()
    {
      return ICON;
    }

    @Override
    public String toString()
    {
      if (trans != null) {
        if (trans.getFlag().isFlagSet(0)) {
          // Transition contains text
          String text = StringResource.getStringRef(trans.getAssociatedText().getValue());
          if (text.length() > MAX_LENGTH) {
            text = text.substring(0, MAX_LENGTH) + "...";
          }
          String dlg = getDialog().getResourceEntry().getResourceName();
          if (trans.getNextDialog().isEmpty() ||
              trans.getNextDialog().getResourceName().equalsIgnoreCase(dlg)) {
            return String.format("%1$s: %2$s", trans.getName(), text);
          } else {
            return String.format("%1$s: %2$s [%3$s]",
                                 trans.getName(), text, trans.getNextDialog().getResourceName());
          }
        } else {
          // Transition contains no text
          String dlg = getDialog().getResourceEntry().getResourceName();
          if (trans.getNextDialog().isEmpty() ||
              trans.getNextDialog().getResourceName().equalsIgnoreCase(dlg)) {
            return String.format("%1$s: (No text)", trans.getName());
          } else {
            return String.format("%1$s: (No text) [%2$s]",
                                 trans.getName(), trans.getNextDialog().getResourceName());
          }
        }
      } else {
        return "(Invalid response)";
      }
    }
  }


  // Creates and manages the dialog tree structure
  private static final class DlgTreeModel implements TreeModel
  {
    // Max. node depth allowed to search or map the tree model
    private static final int MAX_DEPTH = 100;

    private enum ParamType { State, StateTrigger, Transition, ResponseTrigger, Action, Strref }

    private final ArrayList<TreeModelListener> listeners = new ArrayList<TreeModelListener>();
    // maps dialog resources to tables of state index/item pairs
    private final HashMap<String, HashMap<Integer, StateItem>> mapState = new HashMap<String, HashMap<Integer, StateItem>>();
    // maps dialog resources to tables of transition index/item pairs
    private final HashMap<String, HashMap<Integer, TransitionItem>> mapTransition = new HashMap<String, HashMap<Integer, TransitionItem>>();

    private RootItem root;
    private DlgResource dlg;
    private DefaultMutableTreeNode nodeRoot;

    public DlgTreeModel(DlgResource dlg)
    {
      reset(dlg);
    }

  //--------------------- Begin Interface TreeModel ---------------------

    @Override
    public Object getRoot()
    {
      return updateNodeChildren(nodeRoot);
    }

    @Override
    public Object getChild(Object parent, int index)
    {
      DefaultMutableTreeNode node = null;
      if (parent instanceof DefaultMutableTreeNode) {
        DefaultMutableTreeNode nodeParent = (DefaultMutableTreeNode)parent;
        nodeParent = updateNodeChildren(nodeParent);
        if (index >= 0 && index < nodeParent.getChildCount()) {
          node = (DefaultMutableTreeNode)nodeParent.getChildAt(index);
        }
      }
      return updateNodeChildren(node);
    }

    @Override
    public int getChildCount(Object parent)
    {
      if (parent instanceof DefaultMutableTreeNode) {
        DefaultMutableTreeNode nodeParent = (DefaultMutableTreeNode)parent;
        nodeParent = updateNodeChildren(nodeParent);
        return nodeParent.getChildCount();
      }
      return 0;
    }

    @Override
    public boolean isLeaf(Object node)
    {
      if (node instanceof DefaultMutableTreeNode) {
        return ((DefaultMutableTreeNode)node).isLeaf();
      }
      return false;
    }

    @Override
    public void valueForPathChanged(TreePath path, Object newValue)
    {
      // immutable
    }

    @Override
    public int getIndexOfChild(Object parent, Object child)
    {
      if (parent instanceof DefaultMutableTreeNode && child instanceof DefaultMutableTreeNode) {
        DefaultMutableTreeNode nodeParent = (DefaultMutableTreeNode)parent;
        for (int i = 0; i < nodeParent.getChildCount(); i++) {
          TreeNode nodeChild = nodeParent.getChildAt(i);
          if (nodeChild == child) {
            return i;
          }
        }
      }
      return -1;
    }

    @Override
    public void addTreeModelListener(TreeModelListener l)
    {
      if (l != null && !listeners.contains(l)) {
        listeners.add(l);
      }
    }

    @Override
    public void removeTreeModelListener(TreeModelListener l)
    {
      if (l != null) {
        int idx = listeners.indexOf(l);
        if (idx >= 0) {
          listeners.remove(idx);
        }
      }
    }

  //--------------------- End Interface TreeModel ---------------------

    public void nodeChanged(TreeNode node)
    {
      if (node != null) {
        if (node.getParent() == null) {
          fireTreeNodesChanged(this, null, null, null);
        } else {
          fireTreeNodesChanged(this, createNodePath(node.getParent()),
                               new int[]{getChildNodeIndex(node)}, new Object[]{node});
        }
      }
    }

//    public void nodesChanged(TreeNode node, int[] childIndices)
//    {
//      if (node != null && childIndices != null) {
//        boolean isValid = true;
//        for (int i = 0; i < childIndices.length; i++) {
//          if (childIndices[i] < 0 || childIndices[i] >= node.getChildCount()) {
//            isValid = false;
//            break;
//          }
//        }
//        if (isValid) {
//          Object[] children = new Object[childIndices.length];
//          for (int i = 0; i < children.length; i++) {
//            children[i] = node.getChildAt(childIndices[i]);
//          }
//          fireTreeNodesChanged(this, createNodePath(node), childIndices, children);
//        }
//      } else {
//        fireTreeNodesChanged(this, null, null, null);
//      }
//    }

    public void nodeStructureChanged(TreeNode node)
    {
      if (node.getParent() == null) {
        fireTreeStructureChanged(this, null, null, null);
      } else {
        fireTreeStructureChanged(this, createNodePath(node.getParent()),
                                 new int[getChildNodeIndex(node)], new Object[]{node});
      }
    }

//    public void nodesWereInserted(TreeNode node, int[] childIndices)
//    {
//      if (node != null && childIndices != null) {
//        boolean isValid = true;
//        for (int i = 0; i < childIndices.length; i++) {
//          if (childIndices[i] < 0 || childIndices[i] >= node.getChildCount()) {
//            isValid = false;
//            break;
//          }
//        }
//        if (isValid) {
//          Object[] children = new Object[childIndices.length];
//          for (int i = 0; i < children.length; i++) {
//            children[i] = node.getChildAt(childIndices[i]);
//          }
//          fireTreeNodesInserted(this, createNodePath(node), childIndices, children);
//        }
//      }
//    }

//    public void nodesWereRemoved(TreeNode node, int[] childIndices, Object[] removedChildren)
//    {
//      fireTreeNodesRemoved(this, createNodePath(node), childIndices, removedChildren);
//    }

    /** Removes any old content and re-initializes the model with the data from the given dialog resource. */
    public void reset(DlgResource dlg)
    {
      // clearing maps
      Iterator<String> iter = mapState.keySet().iterator();
      while (iter.hasNext()) {
        HashMap<Integer, StateItem> map = mapState.get(iter.next());
        if (map != null) {
          map.clear();
        }
      }
      mapState.clear();

      iter = mapTransition.keySet().iterator();
      while (iter.hasNext()) {
        HashMap<Integer, TransitionItem> map = mapTransition.get(iter.next());
        if (map != null) {
          map.clear();
        }
      }
      mapTransition.clear();

      root = null;
      nodeRoot = null;

      this.dlg = dlg;

      root = new RootItem(dlg);
      for (int i = 0; i < root.getInitialStateCount(); i++) {
        initState(root.getInitialState(i));
      }
      nodeRoot = new DefaultMutableTreeNode(root, true);

      // notifying listeners
      nodeStructureChanged((DefaultMutableTreeNode)getRoot());
    }

    /**
     * Returns a fully qualified TreePath object from root to the specified node.
     * Returns a root node TreePath object on error.
     */
    public TreePath getTreePath(TreeNode node)
    {
      if (node != null) {
        // building reverse tree path
        Stack<TreeNode> stack = new Stack<TreeNode>();
        while (node != null) {
          stack.push(node);
          node = node.getParent();
        }

        // returning valid TreePath object
        TreeNode[] nodes = new TreeNode[stack.size()];
        for (int i = 0; i < nodes.length; i++) {
          nodes[i] = stack.pop();
        }
        return new TreePath(nodes);
      }

      // defaults to selecting root node
      return new TreePath(getRoot());
    }

    /** Returns the first available StateItem node matching the given state index. */
    public DefaultMutableTreeNode findStateNode(int stateIdx)
    {
      return searchTreeNode(nodeRoot, ParamType.State, stateIdx, MAX_DEPTH);
    }

    /** Returns the first available TransitionItem node matching the given transition index. */
    public DefaultMutableTreeNode findTransitionNode(int transIdx)
    {
      return searchTreeNode(nodeRoot, ParamType.Transition, transIdx, MAX_DEPTH);
    }

    /** Returns the first available StateItem node matching the given state trigger index. */
    public DefaultMutableTreeNode findStateTriggerNode(int triggerIdx)
    {
      return searchTreeNode(nodeRoot, ParamType.StateTrigger, triggerIdx, MAX_DEPTH);
    }

    /** Returns the first available TransitionItem node matching the given response trigger index. */
    public DefaultMutableTreeNode findResponseTriggerNode(int triggerIdx)
    {
      return searchTreeNode(nodeRoot, ParamType.ResponseTrigger, triggerIdx, MAX_DEPTH);
    }

    /** Returns the first available TransitionItem node matching the given action index. */
    public DefaultMutableTreeNode findActionNode(int actionIdx)
    {
      return searchTreeNode(nodeRoot, ParamType.Action, actionIdx, MAX_DEPTH);
    }

    /**
     * Returns the first available StateItem or TransitionItem node matching the given string reference
     * of their associated text messages
     */
    public DefaultMutableTreeNode findStringRefNode(int strref)
    {
      return searchTreeNode(nodeRoot, ParamType.Strref, strref, MAX_DEPTH);
    }

    public void updateState(State state)
    {
      if (state != null) {
        int stateIdx = state.getNumber();
        HashMap<Integer, StateItem> map = mapState.get(dlg.getResourceEntry().getResourceName());
        if (map != null) {
          Iterator<Integer> iter = map.keySet().iterator();
          while (iter.hasNext()) {
            StateItem item = map.get(iter.next());
            if (item != null && item.getState().getNumber() == stateIdx) {
              item.setState(state);
              triggerNodeChanged((DefaultMutableTreeNode)getRoot(), item);
              break;
            }
          }
        }
      }
    }

    public void updateTransition(Transition trans)
    {
      if (trans != null) {
        int transIdx = trans.getNumber();
        HashMap<Integer, TransitionItem> map = mapTransition.get(dlg.getResourceEntry().getResourceName());
        if (map != null) {
          Iterator<Integer> iter = map.keySet().iterator();
          while (iter.hasNext()) {
            TransitionItem item = map.get(iter.next());
            if (item != null && item.getTransition().getNumber() == transIdx) {
              item.setTransition(trans);
              triggerNodeChanged((DefaultMutableTreeNode)getRoot(), item);
              break;
            }
          }
        }
      }
    }

    public void updateRoot()
    {
      root = new RootItem(dlg);
      nodeRoot.setUserObject(root);
      nodeChanged(nodeRoot);
    }

    // Recursively parses the tree and triggers a nodeChanged event for each node containing data.
    private void triggerNodeChanged(DefaultMutableTreeNode node, Object data)
    {
      if (node != null && data != null) {
        if (node.getUserObject() == data) {
          nodeChanged(node);
        }
        for (int i = 0; i < node.getChildCount(); i++) {
          triggerNodeChanged((DefaultMutableTreeNode)node.getChildAt(i), data);
        }
      }
    }

    // Generates an array of TreeNode objects from root to specified node
    private Object[] createNodePath(TreeNode node)
    {
      Object[] retVal;
      if (node != null) {
        Stack<TreeNode> stack = new Stack<TreeNode>();
        while (node != null) {
          stack.push(node);
          node = node.getParent();
        }
        retVal = new Object[stack.size()];
        for (int i = 0; i < retVal.length; i++) {
          retVal[i] = stack.pop();
        }
        return retVal;
      } else {
        retVal = new Object[0];
      }
      return retVal;
    }

    // Determines the child index based on the specified node's parent
    private int getChildNodeIndex(TreeNode node)
    {
      int retVal = 0;
      if (node != null && node.getParent() != null) {
        TreeNode parent = node.getParent();
        for (int i = 0; i < parent.getChildCount(); i++) {
          if (parent.getChildAt(i) == node) {
            retVal = i;
            break;
          }
        }
      }
      return retVal;
    }

    private void fireTreeNodesChanged(Object source, Object[] path, int[] childIndices,
                                      Object[] children)
    {
      if (!listeners.isEmpty()) {
        TreeModelEvent event;
        if (path == null || path.length == 0) {
          event = new TreeModelEvent(source, (TreePath)null);
        } else {
          event = new TreeModelEvent(source, path, childIndices, children);
        }
        for (int i = listeners.size()-1; i >= 0; i--) {
          TreeModelListener tml = listeners.get(i);
          tml.treeNodesChanged(event);
        }
      }
    }

//    private void fireTreeNodesInserted(Object source, Object[] path, int[] childIndices,
//                                       Object[] children)
//    {
//      if (!listeners.isEmpty()) {
//        TreeModelEvent event;
//        if (path == null || path.length == 0) {
//          event = new TreeModelEvent(source, (TreePath)null);
//        } else {
//          event = new TreeModelEvent(source, path, childIndices, children);
//        }
//        for (int i = listeners.size()-1; i >= 0; i--) {
//          TreeModelListener tml = listeners.get(i);
//          tml.treeNodesInserted(event);
//        }
//      }
//    }

//    private void fireTreeNodesRemoved(Object source, Object[] path, int[] childIndices,
//                                       Object[] children)
//    {
//      if (!listeners.isEmpty()) {
//        TreeModelEvent event;
//        if (path == null || path.length == 0) {
//          event = new TreeModelEvent(source, (TreePath)null);
//        } else {
//          event = new TreeModelEvent(source, path, childIndices, children);
//        }
//        for (int i = listeners.size()-1; i >= 0; i--) {
//          TreeModelListener tml = listeners.get(i);
//          tml.treeNodesRemoved(event);
//        }
//      }
//    }

    private void fireTreeStructureChanged(Object source, Object[] path, int[] childIndices,
                                       Object[] children)
    {
      if (!listeners.isEmpty()) {
        TreeModelEvent event;
        if (path == null || path.length == 0) {
          event = new TreeModelEvent(source, (TreePath)null);
        } else {
          event = new TreeModelEvent(source, path, childIndices, children);
        }
        for (int i = listeners.size()-1; i >= 0; i--) {
          TreeModelListener tml = listeners.get(i);
          tml.treeStructureChanged(event);
        }
      }
    }

    // Recursively searches tree nodes based on the specified parameters up to a max. node depth
    private DefaultMutableTreeNode searchTreeNode(DefaultMutableTreeNode parent,
                                                  ParamType param, int value, int maxDepth)
    {
      if (parent != null && maxDepth > 0) {
        parent = updateNodeChildren(parent);

        ItemBase item = null;
        if (parent.getUserObject() instanceof ItemBase) {
          item = (ItemBase)parent.getUserObject();
        }

        boolean isDlg = false;
        if (item != null) {
          isDlg = item.getDialogName().equals(dlg.getResourceEntry().getResourceName());
        }

        // checking node properties
        switch (param) {
          case State:
            if (isDlg && item instanceof StateItem &&
                ((StateItem)item).getState().getNumber() == value) {
              return parent;
            }
            break;
          case StateTrigger:
            if (isDlg && item instanceof StateItem &&
                ((StateItem)item).getState().getTriggerIndex() == value) {
              return parent;
            }
            break;
          case Transition:
            if (isDlg && item instanceof TransitionItem &&
                ((TransitionItem)item).getTransition().getNumber() == value) {
              return parent;
            }
            break;
          case ResponseTrigger:
            if (isDlg && item instanceof TransitionItem &&
                ((TransitionItem)item).getTransition().getTriggerIndex() == value) {
              return parent;
            }
            break;
          case Action:
            if (isDlg && item instanceof TransitionItem &&
                ((TransitionItem)item).getTransition().getActionIndex() == value) {
              return parent;
            }
            break;
          case Strref:
            if (isDlg) {
              if (item instanceof StateItem) {
                if (((StateItem)item).getState().getResponse().getValue() == value) {
                  return parent;
                }
              } else if (item instanceof TransitionItem) {
                if (((TransitionItem)item).getTransition().getAssociatedText().getValue() == value) {
                  return parent;
                } else if (((TransitionItem)item).getTransition().getJournalEntry().getValue() == value) {
                  return parent;
                }
              }
            }
            break;
        }

        // continue searching in child nodes
        for (int i = 0; i < parent.getChildCount(); i++) {
          DefaultMutableTreeNode retVal = searchTreeNode((DefaultMutableTreeNode)parent.getChildAt(i),
                                                         param, value, maxDepth-1);
          if (retVal != null) {
            return retVal;
          }
        }
      }

      // no match found
      return null;
    }


    private void initState(StateItem state)
    {
      if (state != null) {
        DlgResource dlg = state.getDialog();
        HashMap<Integer, StateItem> map = mapState.get(dlg.getResourceEntry().getResourceName());
        if (map == null) {
          map = new HashMap<Integer, StateItem>();
          mapState.put(dlg.getResourceEntry().getResourceName(), map);
        }

        if (!map.containsKey(Integer.valueOf(state.getState().getNumber()))) {
          map.put(Integer.valueOf(state.getState().getNumber()), state);

          for (int i = 0; i < state.getState().getTransCount(); i++) {
            int transIdx = state.getState().getFirstTrans() + i;
            StructEntry entry = dlg.getAttribute(String.format(Transition.FMT_NAME, transIdx));
            if (entry instanceof Transition) {
              initTransition(new TransitionItem(dlg, (Transition)entry));
            }
          }
        }
      }
    }

    private void initTransition(TransitionItem trans)
    {
      if (trans != null) {
        DlgResource dlg = trans.getDialog();
        HashMap<Integer, TransitionItem> map = mapTransition.get(dlg.getResourceEntry().getResourceName());
        if (map == null) {
          map = new HashMap<Integer, TransitionItem>();
          mapTransition.put(dlg.getResourceEntry().getResourceName(), map);
        }

        if (!map.containsKey(Integer.valueOf(trans.getTransition().getNumber()))) {
          map.put(Integer.valueOf(trans.getTransition().getNumber()), trans);

          if (!trans.getTransition().getFlag().isFlagSet(3)) {
            // dialog continues
            ResourceRef dlgRef = trans.getTransition().getNextDialog();
            int stateIdx = trans.getTransition().getNextDialogState();
            dlg = getDialogResource(dlgRef.getResourceName());
            if (dlg != null && stateIdx >= 0) {
              StructEntry entry = dlg.getAttribute(String.format(State.FMT_NAME, stateIdx));
              if (entry instanceof State) {
                initState(new StateItem(dlg, (State)entry));
              }
            }
          }
        }
      }
    }

    // Returns a dialog resource object based on the specified resource name
    // Reuses exising DlgResource objects if available
    private DlgResource getDialogResource(String dlgName)
    {
      if (dlgName != null) {
        if (mapState.containsKey(dlgName)) {
          HashMap<Integer, StateItem> map = mapState.get(dlgName);
          if (!map.keySet().isEmpty()) {
            return map.get(map.keySet().iterator().next()).getDialog();
          }
        } else if (mapTransition.containsKey(dlgName)) {
          HashMap<Integer, TransitionItem> map = mapTransition.get(dlgName);
          if (!map.keySet().isEmpty()) {
            return map.get(map.keySet().iterator().next()).getDialog();
          }
        } else if (ResourceFactory.getInstance().resourceExists(dlgName)) {
          try {
            return new DlgResource(ResourceFactory.getInstance().getResourceEntry(dlgName));
          } catch (Exception e) {
            e.printStackTrace();
          }
        }
      }
      return null;
    }

    // Adds all available child nodes to the given parent node
    private DefaultMutableTreeNode updateNodeChildren(DefaultMutableTreeNode parent)
    {
      if (parent != null) {
        if (parent.getUserObject() instanceof StateItem) {
          return updateStateNodeChildren(parent);
        } else if (parent.getUserObject() instanceof TransitionItem) {
          return updateTransitionNodeChildren(parent);
        } else if (parent.getUserObject() instanceof RootItem) {
          return updateRootNodeChildren(parent);
        }
      }
      return parent;
    }

    // Adds all available transition child nodes to the given parent state node
    private DefaultMutableTreeNode updateStateNodeChildren(DefaultMutableTreeNode parent)
    {
      if (parent != null && parent.getUserObject() instanceof StateItem) {
        StateItem state = (StateItem)parent.getUserObject();
        String dlgName = state.getDialog().getResourceEntry().getResourceName();
        int count = state.getState().getTransCount();
        while (parent.getChildCount() < count) {
          int transIdx = state.getState().getFirstTrans() + parent.getChildCount();
          TransitionItem child = mapTransition.get(dlgName).get(Integer.valueOf(transIdx));
          boolean allowChildren = !child.getTransition().getFlag().isFlagSet(3);
          DefaultMutableTreeNode nodeChild = new DefaultMutableTreeNode(child, allowChildren);
          parent.add(nodeChild);
        }
      }
      return parent;
    }

    // Adds all available state child nodes to the given parent transition node
    private DefaultMutableTreeNode updateTransitionNodeChildren(DefaultMutableTreeNode parent)
    {
      if (parent != null && parent.getUserObject() instanceof TransitionItem) {
        // transitions only allow a single state as child
        if (parent.getChildCount() < 1) {
          TransitionItem trans = (TransitionItem)parent.getUserObject();
          ResourceRef dlgRef = trans.getTransition().getNextDialog();
          if (!dlgRef.isEmpty()) {
            String dlgName = dlgRef.getResourceName();
            int stateIdx = trans.getTransition().getNextDialogState();
            StateItem child = mapState.get(dlgName).get(Integer.valueOf(stateIdx));
            DefaultMutableTreeNode nodeChild = new DefaultMutableTreeNode(child, true);
            parent.add(nodeChild);
          }
        }
      }
      return parent;
    }

    // Adds all available initial state child nodes to the given parent root node
    private DefaultMutableTreeNode updateRootNodeChildren(DefaultMutableTreeNode parent)
    {
      if (parent != null && parent.getUserObject() instanceof RootItem) {
        RootItem root = (RootItem)parent.getUserObject();
        while (parent.getChildCount() < root.getInitialStateCount()) {
          int stateIdx = parent.getChildCount();
          StateItem child = root.getInitialState(stateIdx);
          DefaultMutableTreeNode nodeChild = new DefaultMutableTreeNode(child, true);
          parent.add(nodeChild);
        }
      }
      return parent;
    }
  }


  // Panel for displaying information about the current dialog state or trigger
  private static final class ItemInfo extends JPanel
  {
    /** Identifies the respective controls for displaying information. */
    private enum Type {
      STATE, STATE_TEXT, STATE_TRIGGER,
      RESPONSE, RESPONSE_FLAGS, RESPONSE_TEXT, RESPONSE_JOURNAL, RESPONSE_TRIGGER, RESPONSE_ACTION
    }

    private static final String CARD_EMPTY    = "Empty";
    private static final String CARD_STATE    = "State";
    private static final String CARD_RESPONSE = "Response";

    private final CardLayout cardLayout;
    private final JPanel pMainPanel, pState, pResponse, pStateText, pStateTrigger, pResponseFlags,
                         pResponseText, pResponseJournal, pResponseTrigger, pResponseAction;
    private final JTextArea taStateText, taStateTrigger;
    private final JTextArea taResponseText, taResponseJournal, taResponseTrigger, taResponseAction;
    private final JTextField tfResponseFlags;


    public ItemInfo()
    {
      setLayout(new GridBagLayout());

      GridBagConstraints gbc = new GridBagConstraints();

      cardLayout = new CardLayout(0, 0);
      pMainPanel = new JPanel(cardLayout);
      gbc = ViewerUtil.setGBC(gbc, 0, 0, 1, 1, 1.0, 1.0, GridBagConstraints.FIRST_LINE_START,
                              GridBagConstraints.BOTH, new Insets(8, 8, 8, 8), 0, 0);
      add(pMainPanel, gbc);

      // shown when no item has been selected
      pMainPanel.add(new JPanel(), CARD_EMPTY);

      // initializing state item info
      pState = new JPanel(new GridBagLayout());
      pState.setBorder(BorderFactory.createTitledBorder("State"));
      pMainPanel.add(pState, CARD_STATE);

      taStateText = createReadOnlyTextArea();
      pStateText = new JPanel(new BorderLayout());
      pStateText.setBorder(BorderFactory.createTitledBorder("Associated text"));
      pStateText.add(taStateText, BorderLayout.CENTER);

      taStateTrigger = createReadOnlyTextArea();
      pStateTrigger = new JPanel(new BorderLayout());
      pStateTrigger.setBorder(BorderFactory.createTitledBorder("State trigger"));
      pStateTrigger.add(taStateTrigger, BorderLayout.CENTER);

      gbc = ViewerUtil.setGBC(gbc, 0, 0, 1, 1, 1.0, 0.0, GridBagConstraints.FIRST_LINE_START,
                              GridBagConstraints.HORIZONTAL, new Insets(8, 8, 0, 8), 0, 0);
      pState.add(pStateText, gbc);
      gbc = ViewerUtil.setGBC(gbc, 0, 1, 1, 1, 1.0, 0.0, GridBagConstraints.FIRST_LINE_START,
                              GridBagConstraints.HORIZONTAL, new Insets(8, 8, 0, 8), 0, 0);
      pState.add(pStateTrigger, gbc);
      gbc = ViewerUtil.setGBC(gbc, 0, 2, 1, 1, 1.0, 1.0, GridBagConstraints.FIRST_LINE_START,
                              GridBagConstraints.BOTH, new Insets(0, 0, 0, 0), 0, 0);
      pState.add(new JPanel(), gbc);


      // initializing response item info
      pResponse = new JPanel(new GridBagLayout());
      pResponse.setBorder(BorderFactory.createTitledBorder("Response"));
      pMainPanel.add(pResponse, CARD_RESPONSE);

      tfResponseFlags = createReadOnlyTextField();
      pResponseFlags = new JPanel(new BorderLayout());
      pResponseFlags.setBorder(BorderFactory.createTitledBorder("Flags"));
      pResponseFlags.add(tfResponseFlags, BorderLayout.CENTER);

      taResponseText = createReadOnlyTextArea();
      pResponseText = new JPanel(new BorderLayout());
      pResponseText.setBorder(BorderFactory.createTitledBorder("Associated text"));
      pResponseText.add(taResponseText, BorderLayout.CENTER);

      taResponseJournal = createReadOnlyTextArea();
      pResponseJournal = new JPanel(new BorderLayout());
      pResponseJournal.setBorder(BorderFactory.createTitledBorder("Journal entry"));
      pResponseJournal.add(taResponseJournal, BorderLayout.CENTER);

      taResponseTrigger = createReadOnlyTextArea();
      pResponseTrigger = new JPanel(new BorderLayout());
      pResponseTrigger.setBorder(BorderFactory.createTitledBorder("Response trigger"));
      pResponseTrigger.add(taResponseTrigger, BorderLayout.CENTER);

      taResponseAction = createReadOnlyTextArea();
      pResponseAction = new JPanel(new BorderLayout());
      pResponseAction.setBorder(BorderFactory.createTitledBorder("Action"));
      pResponseAction.add(taResponseAction, BorderLayout.CENTER);

      gbc = ViewerUtil.setGBC(gbc, 0, 0, 1, 1, 1.0, 0.0, GridBagConstraints.FIRST_LINE_START,
                              GridBagConstraints.HORIZONTAL, new Insets(8, 8, 0, 8), 0, 0);
      pResponse.add(pResponseFlags, gbc);
      gbc = ViewerUtil.setGBC(gbc, 0, 1, 1, 1, 1.0, 0.0, GridBagConstraints.FIRST_LINE_START,
                              GridBagConstraints.HORIZONTAL, new Insets(8, 8, 0, 8), 0, 0);
      pResponse.add(pResponseText, gbc);
      gbc = ViewerUtil.setGBC(gbc, 0, 2, 1, 1, 1.0, 0.0, GridBagConstraints.FIRST_LINE_START,
                              GridBagConstraints.HORIZONTAL, new Insets(8, 8, 0, 8), 0, 0);
      pResponse.add(pResponseJournal, gbc);
      gbc = ViewerUtil.setGBC(gbc, 0, 3, 1, 1, 1.0, 0.0, GridBagConstraints.FIRST_LINE_START,
                              GridBagConstraints.HORIZONTAL, new Insets(8, 8, 0, 8), 0, 0);
      pResponse.add(pResponseTrigger, gbc);
      gbc = ViewerUtil.setGBC(gbc, 0, 4, 1, 1, 1.0, 0.0, GridBagConstraints.FIRST_LINE_START,
                              GridBagConstraints.HORIZONTAL, new Insets(8, 8, 0, 8), 0, 0);
      pResponse.add(pResponseAction, gbc);
      gbc = ViewerUtil.setGBC(gbc, 0, 5, 1, 1, 1.0, 1.0, GridBagConstraints.FIRST_LINE_START,
                              GridBagConstraints.BOTH, new Insets(0, 0, 0, 0), 0, 0);
      pResponse.add(new JPanel(), gbc);

      showPanel(CARD_EMPTY);
    }

    /** Shows the panel of given name. */
    public void showPanel(String cardName)
    {
      if (cardName != null) {
        if (cardName.equals(CARD_STATE)) {
          clearCard(CARD_RESPONSE);
        } else if (cardName.equals(CARD_RESPONSE)) {
          clearCard(CARD_STATE);
        } else {
          cardName = CARD_EMPTY;
          clearCard(CARD_STATE);
          clearCard(CARD_RESPONSE);
        }
        cardLayout.show(pMainPanel, cardName);
      }
    }

    /** Enable/disable visibility of the given control. */
    public void showControl(Type type, boolean b)
    {
      getControl(type).setVisible(b);
    }

    /** Update border title of the given control. */
    public void updateControlBorder(Type type, String title)
    {
      if (title == null) title = "";
      getControl(type).setBorder(BorderFactory.createTitledBorder(title));
    }

    /** Update content of the given control. */
    public void updateControlText(Type type, String text)
    {
      if (text == null) text = "";
      switch (type) {
        case STATE_TEXT:        taStateText.setText(text); break;
        case STATE_TRIGGER:     taStateTrigger.setText(text); break;
        case RESPONSE_FLAGS:    tfResponseFlags.setText(text); break;
        case RESPONSE_TEXT:     taResponseText.setText(text); break;
        case RESPONSE_JOURNAL:  taResponseJournal.setText(text); break;
        case RESPONSE_TRIGGER:  taResponseTrigger.setText(text); break;
        case RESPONSE_ACTION:   taResponseAction.setText(text); break;
        default:
      }
    }


    // Returns the given control
    private JPanel getControl(Type type)
    {
      switch (type) {
        case STATE:             return pState;
        case RESPONSE:          return pResponse;
        case STATE_TEXT:        return pStateText;
        case STATE_TRIGGER:     return pStateTrigger;
        case RESPONSE_FLAGS:    return pResponseFlags;
        case RESPONSE_TEXT:     return pResponseText;
        case RESPONSE_JOURNAL:  return pResponseJournal;
        case RESPONSE_TRIGGER:  return pResponseTrigger;
        case RESPONSE_ACTION:   return pResponseAction;
      }
      return new JPanel();
    }

    // Clears and disables controls in the specified panel
    private void clearCard(String cardName)
    {
      if (cardName != null) {
        if (cardName.equals(CARD_STATE)) {
          updateControlText(Type.STATE_TEXT, "");
          showControl(Type.STATE_TEXT, false);
          updateControlText(Type.STATE_TRIGGER, "");
          showControl(Type.STATE_TRIGGER, false);
        } else if (cardName.equals(CARD_RESPONSE)) {
          updateControlText(Type.RESPONSE_FLAGS, "");
          showControl(Type.RESPONSE_FLAGS, false);
          updateControlText(Type.RESPONSE_TEXT, "");
          showControl(Type.RESPONSE_TEXT, false);
          updateControlText(Type.RESPONSE_JOURNAL, "");
          showControl(Type.RESPONSE_JOURNAL, false);
          updateControlText(Type.RESPONSE_TRIGGER, "");
          showControl(Type.RESPONSE_TRIGGER, false);
          updateControlText(Type.RESPONSE_ACTION, "");
          showControl(Type.RESPONSE_ACTION, false);
        }
      }
    }

    // Helper method for creating a read-only textarea component
    private JTextArea createReadOnlyTextArea()
    {
      JLabel l = new JLabel();
      JTextArea ta = new JTextArea();
      ta.setEditable(false);
      ta.setFont(l.getFont());
      ta.setBackground(l.getBackground());
      ta.setWrapStyleWord(true);
      ta.setLineWrap(true);
      l = null;

      return ta;
    }

    // Helper method for creating a read-only textfield component
    private JTextField createReadOnlyTextField()
    {
      JLabel l = new JLabel();
      JTextField tf = new JTextField();
      tf.setBorder(BorderFactory.createEmptyBorder());
      tf.setEditable(false);
      tf.setFont(l.getFont());
      tf.setBackground(l.getBackground());
      l = null;

      return tf;
    }
  }
}