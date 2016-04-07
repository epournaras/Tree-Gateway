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

package testApp;

import java.util.Iterator;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import protopeer.Experiment;
import dsutil.protopeer.FingerDescriptor;
import protopeer.Peer;
import protopeer.PeerFactory;
import dsutil.generic.RankPriority;
import protopeer.SimulatedExperiment;
import protopeer.network.NetworkAddress;
import protopeer.servers.bootstrap.SimplePeerIdentifierGenerator;
import protopeer.util.NetworkAddressPair;
import protopeer.util.quantities.Time;
import dsutil.protopeer.services.topology.trees.DescriptorType;
import dsutil.protopeer.services.topology.trees.TreeProvider;
import dsutil.protopeer.services.topology.trees.TreeType;
import tree.centralized.client.TreeClient;
import tree.centralized.server.TreeServer;

/**
 * Performs aggregation by acquiring a tree from the client-server bootstraping
 * mechanism.
 *
 * @author Evangelos
 */
public class ClientServerTreeAggregation extends SimulatedExperiment {
    
    //Simulation Parameters
    private final static int runDuration=400;
    private final static int N=100;
    private final static int[] v=new int[]{3};
    
    private static final RankPriority priority=RankPriority.HIGH_RANK;
    private static final DescriptorType descriptor=DescriptorType.RANK;
    private static final TreeType type=TreeType.SORTED_HtL;

    public static void main(String[] args) {
        
        System.out.println("System started.");
        Experiment.initEnvironment();
        ClientServerTreeAggregation exp = new ClientServerTreeAggregation();
        exp.init();
        PeerFactory peerFactory=new PeerFactory() {
            public Peer createPeer(int peerIndex, Experiment experiment) {
                Peer newPeer = new Peer(peerIndex);
                if (peerIndex == 0) {
                   newPeer.addPeerlet(new TreeServer(N, priority, descriptor, type));
                }
                newPeer.addPeerlet(new TreeClient(Experiment.getSingleton().getAddressToBindTo(0), new SimplePeerIdentifierGenerator(), Math.random(), v[(int)(Math.random()*v.length)]));
                newPeer.addPeerlet(new TreeProvider());
                newPeer.addPeerlet(new Aggregator(Math.random(), 3000));
                return newPeer;
            }
        };
        exp.initPeers(0,N,peerFactory);
        exp.startPeers(0,N);
        //run the simulation
        exp.runSimulation(Time.inSeconds(runDuration));
//        AETOSLogReplayer replayer=new AETOSLogReplayer("peersLog/"+folder.getName()+"/", 0, 50);





//        AETOSVisualizer viz=new AETOSVisualizer();
//        ConcurrentHashMap<NetworkAddress, FingerDescriptor> vertices=new ConcurrentHashMap<NetworkAddress, FingerDescriptor>();
//        Set descriptors = exp.getRootMeasurementLog().getTagsOfExactType(FingerDescriptor.class);
//        Set edges = exp.getRootMeasurementLog().getTagsOfExactType(NetworkAddressPair.class);
//        System.out.println(descriptors.size());
//        System.out.println(edges.size());
//        if(edges.size()!=0){
//             Iterator it=descriptors.iterator();
//             while(it.hasNext()){
//                 FingerDescriptor vertex=(FingerDescriptor)it.next();
//                 vertices.put(vertex.getNetworkAddress(), vertex);
//             }
//             viz.buildGraph(edges, vertices);
//             viz.drawGraph();
//        }

        System.out.println("System finished.");
    }

}
