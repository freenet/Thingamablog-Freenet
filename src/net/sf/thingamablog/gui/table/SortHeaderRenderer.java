/*
 * Copyright (C) 2003  Bob Tantlinger
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 * 
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place - Suite 330, Boston, MA  02111-1307, USA.
 *
 */


package net.sf.thingamablog.gui.table;
import java.awt.Component;

import javax.swing.Icon;
import javax.swing.JTable;
import javax.swing.UIManager;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.JTableHeader;


public class SortHeaderRenderer extends DefaultTableCellRenderer
{
    /**
     * 
     */
    private static final long serialVersionUID = 1L;
    public static Icon NONSORTED =
        new SortArrowIcon(SortArrowIcon.NONE);
    public static Icon ASCENDING =
        new SortArrowIcon(SortArrowIcon.ASCENDING);
    public static Icon DECENDING =
        new SortArrowIcon(SortArrowIcon.DECENDING);
  
    public SortHeaderRenderer()
    {
        setHorizontalTextPosition(LEFT);        
		setHorizontalAlignment(LEFT);
    }
  
    public Component getTableCellRendererComponent(
        JTable table, Object value, boolean isSelected,
        boolean hasFocus, int row, int col)
    {
        int index = -1;
        boolean ascending = true;
        if (table instanceof JSortTable)
        {
            JSortTable sortTable = (JSortTable)table;
            index = sortTable.getSortedColumnIndex();
            ascending = sortTable.isSortedColumnAscending();
        }
        if (table != null)
        {
            JTableHeader header = table.getTableHeader();
            if (header != null)
            {
                setForeground(header.getForeground());
                setBackground(header.getBackground());
                setFont(header.getFont());
            }
        }
        
        Icon icon = ascending ? ASCENDING : DECENDING;
		setIcon((col == index) ? icon : NONSORTED);
		
		//System.out.println(value);
        
        if(value == null)
        {
        	setText("");
        }
        //else if(value instanceof ImageIcon)
        //{        	
        //	setText("");
		//	setIcon((Icon)value);
        //}
        else
        {
        	setText(value.toString());        	
        }
        setBorder(UIManager.getBorder("TableHeader.cellBorder"));
        return this;
    }
}

