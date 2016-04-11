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

import java.util.HashSet;
import java.util.Map.Entry;
import java.util.Set;
import org.apache.log4j.Logger;
import protopeer.BasePeerlet;
import dsutil.protopeer.FingerDescriptor;
import protopeer.Peer;
import dsutil.generic.RankPriority;
import protopeer.network.Message;
import dsutil.protopeer.services.topology.trees.DescriptorType;
import dsutil.protopeer.services.topology.trees.TreeType;
import tree.BalanceType;
import tree.centralized.TreeViewReply;
import tree.centralized.TreeViewRequest;

/**
 * The server peerlet responsible for managing the bootstrapping a tree topology.
 * It waits for an N number to tree view requests and then it builds an
 * appropriate tree topology for them.
 *
 * The <code>BootstrapServer</code> of ProtoPeer is not appropriate to be used
 * or extended. This is because of the <code>FingerDescriptor</code> usage and
 * the minimum number of requests. This is the simplest version of building a
 * tree topology based on a given number of peers. More dynamic insertions and
 * removals can be a future extension. However, if these options are supported,
 * the <code>TreeServer</code> is not anymore bootstrapper but rather a central
 * mechanism. We leave this for future work.
 *
 * @author Evangelos
 */
public class TreeServer extends BasePeerlet{

    private static final Logger logger = Logger.getLogger(TreeServer.class);
    private enum ServerState {
        INIT,
        WAITING,
        COMPLETED
    }
    private Set<FingerDescriptor> peers;
    private TreeTopologyGenerator generator;
    private ServerState state;
    private final int N;
    private int n;

    /**
     * Initializes the server and the topology generator with the required
     * information.
     *
     * @param N the number of requests waiting before starting building the
     * tree topology
     * @param priority higher or lower ranks prefered during the sorting. This
     * parameter is fed in the <code>TreeTopologyGenerator</code>.
     * @param descrType the descriptor type based on which the sorting is
     * performed. This parameter is fed in
     * the <code>TreeTopologyGenerator</code>.
     * @param treeType the type of tree to be built. This parameter is fed in
     * the <code>TreeTopologyGenerator</code>.
     */
    public TreeServer(int N, RankPriority priority, DescriptorType descrType, TreeType treeType){
        this(N,priority,descrType,treeType,BalanceType.WEIGHT_BALANCED);
    }

    /**
     * Initializes the server and the topology generator with the required
     * information.
     *
     * @param N the number of requests waiting before starting building the
     * tree topology
     * @param priority higher or lower ranks prefered during the sorting. This
     * parameter is fed in the <code>TreeTopologyGenerator</code>.
     * @param descrType the descriptor type based on which the sorting is
     * performed. This parameter is fed in
     * the <code>TreeTopologyGenerator</code>.
     * @param treeType the type of tree to be built. This parameter is fed in
     * the <code>TreeTopologyGenerator</code>.
     * @param balanceType the balance of tree to be built. This parameter is fed in
     * the <code>TreeTopologyGenerator</code>.
     */
    public TreeServer(int N, RankPriority priority, DescriptorType descrType, TreeType treeType, BalanceType balanceType){
        this.state=ServerState.INIT;
        this.N=N;
        this.n=0;
        this.peers=new HashSet<FingerDescriptor>();
        this.generator=new TreeTopologyGenerator(priority, descrType, treeType, balanceType);
    }

    /**
     * Processes the received requests:
     *
     * If the number of received requests reaches N then:
     *
     * The counter increases and the peer sent the request is added in the set of
     * peers participating in the topology.
     *
     * otherwise:
     *
     * The topology generator creates the topology and the server sends back to
     * each peer its tree neighbors.
     *
     * @param request the <code>TreeViewRequest</code> received.
     */
    private void runPassiveState(TreeViewRequest request){
        if (logger.isDebugEnabled()) {
            logger.debug("Received a tree view request from: "+request.sourceDescriptor);
        }
        this.peers.add(request.sourceDescriptor);
        this.n++;
        if(n==N){
            Set<Entry<FingerDescriptor,TreeViewFacilitator>> views=generator.generateTopology(this.peers);
            this.replyViews(views);
            this.state=ServerState.COMPLETED;
        }

    }

    /**
     * Sends the tree views in each peer participating in the topology
     *
     * @param views the topology created by the topology generator
     */
    private void replyViews(Set<Entry<FingerDescriptor,TreeViewFacilitator>> views){
//        MeasurementFileDumper dumper=new MeasurementFileDumper("peersLog/"+"/"+Integer.toString(getPeer().getIndexNumber()));
        if (logger.isDebugEnabled()) {
            logger.debug("Sending tree views to all peers...");
        }
        for(Entry<FingerDescriptor,TreeViewFacilitator> entry:views){
            TreeViewReply reply=new TreeViewReply();
            reply.parent=entry.getValue().getParent();
            reply.children=entry.getValue().getChildren();
//            System.out.println(entry.getValue().getChildren().size());
            getPeer().sendMessage(entry.getKey().getNetworkAddress(), reply);
            getPeer().getMeasurementLogger().log(entry.getKey(), 1);
        }
        

    }

    /**
     * Server enters the waiting state.
     */
    private void runActiveState(){
        this.state=ServerState.WAITING;
    }

    /**
     * Initializes the peer.
     *
     * @param peer the local peer
     */
    @Override
    public void init(Peer peer) {
        super.init(peer);
    }

    /**
     * Starts the peer by calling the entering the active state.
     */
    @Override
    public void start() {
        super.start();
        this.runActiveState();
        if (logger.isDebugEnabled()) {
                logger.debug("BootstrapServer started");
        }
    }

    /**
     * Handling the incoming messages. Processing the <code>TreeViewRequest</code>s
     *
     * @param message the incoming message
     */
    @Override
    public void handleIncomingMessage(Message message) {
        if (message instanceof TreeViewRequest) {
                this.runPassiveState((TreeViewRequest) message);
        }
    }
}
