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

package tree.centralized.client;

import java.util.List;
import org.apache.log4j.Logger;
import protopeer.BasePeerlet;
import dsutil.protopeer.FingerDescriptor;
import protopeer.Peer;
import protopeer.network.Message;
import protopeer.network.NetworkAddress;
import protopeer.servers.bootstrap.BootstrapClient;
import protopeer.servers.bootstrap.PeerIdentifierGenerator;
import protopeer.util.NetworkAddressPair;
import dsutil.protopeer.services.topology.trees.TreeProviderInterface;
import tree.centralized.TreeViewRequest;
import dsutil.protopeer.services.topology.trees.DescriptorType;
import dsutil.protopeer.services.topology.trees.TreeMiddlewareInterface;
import tree.centralized.TreeViewReply;


/**
 * This is the bootstrapping client for a tree overlay. It sends
 * <code>TreeViewRequest</code> to the tree server and waits for a reply. The
 * client provides ranking information and creates the <code>FingerDescriptor</code>s.
 *
 * @author Evangelos
 */
public class TreeClient extends BasePeerlet implements TreeMiddlewareInterface{
    
    private static final Logger logger = Logger.getLogger(BootstrapClient.class);
    private enum ClientState {
        INIT,
        WAITING,
        COMPLETED
    }
    private ClientState state;
    private FingerDescriptor localDescriptor;
    private PeerIdentifierGenerator idGenerator;
    private NetworkAddress bootstrapServerAddress;
    private double rank;
    private int dMax;
    
    /**
     * Initialiazes the tree client with bootstraping and rank information.
     *
     * @param bootstrapServerAddress the network address of thwe tree server
     * @param idGenerator a peer identifier generator
     * @param rank the rank of the local peer
     * @param dMax the node degree of the local peer
     */
    public TreeClient(NetworkAddress bootstrapServerAddress, PeerIdentifierGenerator idGenerator, double rank, int dMax) {
        this.bootstrapServerAddress = bootstrapServerAddress;
        this.idGenerator=idGenerator;
        this.rank=rank;
        this.dMax=dMax;
        this.state=ClientState.INIT;
    }

    /**
     * Creates the local finger descriptor with information for bootstrapping
     * the tree topology.
     *
     * @return the finger descriptor of the local peer
     */
    private FingerDescriptor createFingerDescriptor(){
        this.localDescriptor=new FingerDescriptor(getPeer().getFinger());
        localDescriptor.addDescriptor(DescriptorType.RANK, rank);
        localDescriptor.addDescriptor(DescriptorType.NODE_DEGREE, dMax);
        return localDescriptor;
    }

    /**
     * Accesses the provider service responsible for setting the tree view
     * to the application.
     *
     * @return the tree middleware installed in the peer
     */
    private TreeProviderInterface getTreeProvider(){
        return (TreeProviderInterface) getPeer().getPeerletOfType(TreeProviderInterface.class);
    }

    /**
     * Initializes the peer with creating the peer identifier.
     *
     * @param peer the local peer
    */
    @Override
    public void init(Peer peer) {
        super.init(peer);
        getPeer().setIdentifier(idGenerator.generatePeerIdentifier(getPeer().getNetworkAddress()));
    }

    /**
     * Starts the peer by running the active state.
     */
    @Override
    public void start() {
        super.start();
        this.runActiveState();
    }

    /**
     * This is the active state of the peerlet. Creates the local finger
     * descriptor and sends <code>TreeViewRequest</code> to the tree server. The
     * client then enters the WAITING state.
     */
    private void runActiveState(){
        this.createFingerDescriptor();
        TreeViewRequest request=new TreeViewRequest();
        request.sourceDescriptor=localDescriptor;
        getPeer().sendMessage(this.bootstrapServerAddress, request);
        this.state=ClientState.WAITING;
        if (logger.isDebugEnabled()) {
                logger.debug("Sending " +request + " to " + this.bootstrapServerAddress);
        }
    }

    /**
     * Receives the tree view from the tree server and delivers it to the
     * middlware service. The latter one is responsible to provide the tree
     * view to the application.
     *
     * @param reply the reply from the server containing information about the
     * tree view of the local peer.
     */
    private void runPassiveState(TreeViewReply reply){
        if (logger.isDebugEnabled()) {
            logger.debug("Received a reply from the tree server.");
        }
        this.state=ClientState.COMPLETED;
        this.deliverTreeView(reply.parent, reply.children);
    }

    /**
     * Returns the local finger descriptor that the tree middlware uses.
     *
     * @return the local finger descriptor of the tree middlware
     */
    public FingerDescriptor getMyLocalDescriptor(){
        return this.localDescriptor;
    }

    /**
     * For this tree middlware interface implementation, this method is not used.
     *
     * @param parent the delivered parent
     */
    public void deliverParent(FingerDescriptor parent){

    }

    /**
     * For this tree middlware interface implementation, this method is not used.
     *
     * @param children the delivered children
     */
    public void deliverChildren(List<FingerDescriptor> children){

    }

    /**
     * Receives the tree view from the tree server reply and provides it to the
     * tree provider.
     *
     * @param children the delivered children
     */
    public void deliverTreeView(FingerDescriptor parent, List<FingerDescriptor> children){
        this.getTreeProvider().provideTreeView(parent, children);
        double rp=(Double)parent.getDescriptor(DescriptorType.RANK);
        double ri=(Double)this.localDescriptor.getDescriptor(DescriptorType.RANK);
    }

    /**
     * Handles incoming messages of the type <code>TreeViewReply</code>.
     *
     * @param message the received message.
     */
    @Override
    public void handleIncomingMessage(Message message) {
        if (message instanceof TreeViewReply) {
                this.runPassiveState((TreeViewReply) message);
        }
    }

}
