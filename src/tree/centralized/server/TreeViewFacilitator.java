/*
 * Copyright (C) 2016 Evangelos Pournaras
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
 */
package tree.centralized.server;

import java.util.ArrayList;
import java.util.List;
import dsutil.protopeer.FingerDescriptor;

/**
 * A facilitator for the tree view sent by the tree server to the tree client.
 *
 * @author Evangelos
 */
public class TreeViewFacilitator {

    private FingerDescriptor parent=null;
    private List<FingerDescriptor> children=new ArrayList<FingerDescriptor>();

    /**
     * Adds a child in the children list
     *
     * @param child the finger descriptor of a child
     */
    public List<FingerDescriptor> addChild(FingerDescriptor child){
        getChildren().add(child);
        return getChildren();
    }

    /**
     * @param parent the parent to set
     */
    public void setParent(FingerDescriptor parent) {
        this.parent = parent;
    }

    /**
     * @param children the children to set
     */
    public void setChildren(List<FingerDescriptor> children) {
        this.setChildren(children);
    }

    /**
     * @return the parent
     */
    public FingerDescriptor getParent() {
        return parent;
    }

    /**
     * @return the children
     */
    public List<FingerDescriptor> getChildren() {
        return children;
    }
}
