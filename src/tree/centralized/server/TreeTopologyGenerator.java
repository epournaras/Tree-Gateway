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
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import org.apache.log4j.Logger;
import dsutil.protopeer.FingerDescriptor;
import dsutil.generic.RankPriority;
import dsutil.generic.RankedFingerComparator;
import dsutil.protopeer.services.topology.trees.DescriptorType;
import dsutil.protopeer.services.topology.trees.TreeType;
import tree.BalanceType;

/**
 * Creates various tree topologies given a set of peers facilitated with their
 * descriptors. Returns a <code>Map</code> of <code>TreeViewFacilitator</code>s
 * that defines the topology.
 * 
 * Should implement the <code>TopologyGenerator</code> of ProtoPeer but it is
 * not possible as we want to use the <code>FingerDescriptor</code>. This is
 * because in this way we can build topolgies based on local ranks.
 *
 * @author Evangelos
 */
public class TreeTopologyGenerator {

    private static final Logger logger = Logger.getLogger(TreeTopologyGenerator.class);
    private Map<FingerDescriptor,TreeViewFacilitator> topology;
    private RankPriority priority;
    private DescriptorType descrType;
    private TreeType treeType;
    private BalanceType balanceType;

    /**
     * Requires information about the priority given to the ranks of the peers
     * (high ranks over low ranks or the other way around. The descriptor type
     * based on which the appropriate rank is considered and the tree type that
     * defines the type of topolgy built. The tree is weight balanced per default.
     *
     * @param priority The high ranks or the low ranks priority given for
     * shorting the peers
     * @param descrType The numeric double descriptor type on which the ranks
     * correspond to
     * @param treeType The type of tree built. It can be a random tree, sorted
     * low to high ranks, or high to low ranks.
     */
    public TreeTopologyGenerator(RankPriority priority, DescriptorType descrType, TreeType treeType){
        this(priority, descrType, treeType, BalanceType.WEIGHT_BALANCED);
    }
    
    /**
     * Requires information about the priority given to the ranks of the peers
     * (high ranks over low ranks or the other way around. The descriptor type
     * based on which the appropriate rank is considered and the tree type that
     * defines the type of topolgy built.
     *
     * @param priority The high ranks or the low ranks priority given for
     * shorting the peers
     * @param descrType The numeric double descriptor type on which the ranks
     * correspond to
     * @param treeType The type of tree built. It can be a random tree, sorted
     * low to high ranks, or high to low ranks.
     * @param balanceType Describes how the tree should be balanced. It can be a
     * fully weight balanced tree, or a degenerate tree (i.e. a list)
     */
    public TreeTopologyGenerator(RankPriority priority, DescriptorType descrType, TreeType treeType, BalanceType balanceType){
        this.priority=priority;
        this.descrType=descrType;
        this.treeType=treeType;
        this.balanceType=balanceType;
        this.topology=new HashMap();
    }

    /**
    /**
     * Facilitates a cental algorithm for creating the tree topology. It is
     * based on creating a level-by-level tree based on a list of peers.
     *
     * @param peers The set of peers participating the tree topology.
     * @return an entry set with the tree view for each peer
     */
    public Set<Entry<FingerDescriptor,TreeViewFacilitator>> generateTopology(Set<FingerDescriptor> peers){
        //1. Definition and initializtion of variables
        List<FingerDescriptor> buffer=new ArrayList<FingerDescriptor>();
        buffer.addAll(peers);
        boolean run=true;
        int pLeft=0;
        int pRight=0;
        int cLeft=1;
        int cRight=1;
        //2. Organize the peers appropriatelly
        this.organizePeers(buffer);
        //3. Intializing the topology with the root
        this.initTreeTopology(buffer.get(0));
        //4. Algorithm
        while(run){
            int maxLevelSize=0;
            //4.1 Calculate the size of children level:
            for(int p=pLeft; p<=pRight; p++){
                maxLevelSize+=this.getNumOfChildren(buffer.get(p));
            }
            //4.2 Define the size of the children level
            cLeft=pRight+1;
            cRight=cLeft+maxLevelSize-1;
            //4.3 Create the topology (views)
            int cCounter=cLeft;
            for(int i=pLeft; i<=pRight; i++){
                FingerDescriptor parent=buffer.get(i);
                int numOfChildren=this.getNumOfChildren(parent);
                for(int j=cCounter; j<=cCounter+numOfChildren-1; j++){
                    if(j>=buffer.size()){
                        run=false;
                        break;
                    }
                    FingerDescriptor child=buffer.get(j);
                    TreeViewFacilitator parentView=this.topology.remove(parent);
                    parentView.addChild(child);
                    this.topology.put(parent, parentView);
                    TreeViewFacilitator childView=new TreeViewFacilitator();
                    childView.setParent(parent);
                    this.topology.put(child, childView);
                }
                if(run==false){
                    break;
                }
                cCounter+=numOfChildren;
            }
            if(run==false){
                break;
            }
            //4.4 Shift the parent and children sets and reset variables
            pLeft=cLeft;
            pRight=cRight; // default: each parent can have children (weight balanced)
            if(balanceType == BalanceType.LIST) {
                pLeft = cRight; // only the last parent can have children
            }
        }
        return this.topology.entrySet();
    }

    /**
     * Organizes a list of peers appropriatelly before the tree building
     * algorithm applies. In the case of a random tree, the list is shuffled. In
     * the case of a sorted tree, the peers are sorted according to paramiterized
     * <code>RankFingerComparator<code>.
     *
     * @param buffer the list of peers participating the tree topology
     */
    private void organizePeers(List<FingerDescriptor> buffer){
        if(treeType==treeType.RANDOM){
            Collections.shuffle(buffer);
        }
        else{
            if(treeType==treeType.SORTED_HtL || treeType==treeType.SORTED_LtH){
                Collections.sort(buffer, new RankedFingerComparator(priority, descrType));
            }
            else{
                logger.debug("Incorrect bootstrapping arguments: rank priority has not been found.");
            }
        }
    }

    /**
     * Initializes the topology by inserting the root node.
     *
     * @param root the finger descriptor of the root node
     */
    private void initTreeTopology(FingerDescriptor root){
        TreeViewFacilitator rootView=new TreeViewFacilitator();
        this.topology.put(root, rootView);
    }

    /**
     * Computes the maximum numbe of children from the node degree
     * Maximum # of children= node degree - 1
     *
     * @return the maximum number of children supported by the peer
     */
    private int getNumOfChildren(FingerDescriptor peer){
        return ((Integer)peer.getDescriptor(DescriptorType.NODE_DEGREE))-1;
    }
}
