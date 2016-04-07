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

import java.util.ArrayList;
import java.util.List;
import org.apache.log4j.Logger;
import protopeer.BasePeerlet;
import protopeer.Finger;
import protopeer.Peer;
import protopeer.network.Message;
import protopeer.time.Timer;
import protopeer.time.TimerListener;
import protopeer.util.quantities.Time;
import dsutil.protopeer.services.topology.trees.TreeApplicationInterface;

/**
 * A simple testing application. Nodes keep a value and they aggregate over a
 * tree in which they belong. At the end, the global value is broadcasted to all
 * nodes.
 * 
 * The actions of the aggregator are controled and managed by 5 states:
 * (i) idle, (ii) waiting for the tree view before it the aggregation starts,
 * (iii) waiting aggregates from the children, (iv) waiting a broadcast message
 * from the parent containing the global value, (v) the node has completed the
 * aggregation.
 *
 * @author Evangelos
 */
public class Aggregator extends BasePeerlet implements TreeApplicationInterface{

    private static final Logger logger = Logger.getLogger(Aggregator.class);

    private Finger parent=null;
    private int T;
    private List<Finger> children=new ArrayList<Finger>();
    private double value;
    private double aggregate;
    private double global;
    private int childrCounter;

    private enum AggregationState{
        IDLE,
        WAITING_TREE_VIEW,
        WAITING_AGGREGATES,
        WAITING_BROADCAST,
        COMPLETE
    }

    private AggregationState state;

    /**
     * Initializes the aggregator.
     *
     * @param value the local value of the peer
     * @param T a waiting time for starting the aggregation after receiving the
     * tree view. This delay gurantees that all the other peers have also
     * received the tree view.
    */
    public Aggregator(double value, int T){
        this.value=value;
        this.T=T;
        this.aggregate=0;
        this.childrCounter=0;
        this.global=0;
    }

    /**
     * Initializes the peer with creating the peer identifier and setting the
     * status to idle.
     *
     * @param peer the local peer
    */
    @Override
    public void init(Peer peer) {
        super.init(peer);
        state=AggregationState.IDLE;
    }

    /**
     * Starts the peer by waiting for the tree view.
     */
    @Override
    public void start() {
        super.start();
        this.state=AggregationState.WAITING_TREE_VIEW;
    }

    /**
     * Sets the parent provided by the <code>TreeProvider</code>.
     *
     * @param parent the finger of the parent
    */
    public void setParent(Finger parent){
        this.parent=parent;
    }

    /**
     * Sets the children provided by the <code>TreeProvider</code>.
     *
     * @param children the fingers of the children
    */
    public void setChildren(List<Finger> children){
        this.children.addAll(children);
    }

    /**
     * Sets the tree view provided by the <code>TreeProvider</code>.
     *
     * @param parent the finger of the parent
     * @param children the fingers of the children
    */
    public void setTreeView(Finger parent, List<Finger> children){
        this.parent=parent;
        this.children.addAll(children);
        this.runActiveState();
    }

    /**
     * Scedules the active state of aggregator. It is triggered with a delay
     * after receiving tree view. If the peer is a leaf, it triggers the
     * sending of an <code>AggregationMessage</code>.
     *
     * The aggregator leaves the state of waiting the tree view and enters the
     * waiting for aggregates. At the end of the active state, it waits for a
     * broadcast.
    */
    private void runActiveState(){
        Timer activeStateTimer=getPeer().getClock().createNewTimer();
        activeStateTimer.addTimerListener(new TimerListener() {
            public void timerExpired(Timer timer) {
                state=AggregationState.WAITING_AGGREGATES;
                if(children.size()==0 && parent!=null){
                    AggregationMessage message=createMessage();
                    getPeer().sendMessage(parent.getNetworkAddress(), message);
                    state=AggregationState.WAITING_BROADCAST;
                }
            }
        });
        activeStateTimer.schedule(Time.inMilliseconds(this.T));
    }

    /**
     * It defines the reactions when waiting for aggregate and broadcast messages.
     *
     * @param receivedMess the received <code>AggregationMessage</code>
    */
    private void runPassiveState(AggregationMessage receivedMess){
        switch(state){
            case IDLE:
                logger.debug("Peer has not been initialized yet: State Idle.");
                break;
            case WAITING_TREE_VIEW:
                logger.debug("Tree view is still expected: State Waiting Tree View.");
                break;
            case WAITING_AGGREGATES:
                this.childrCounter++;
                this.aggregate+=receivedMess.aggregate;
                if(this.childrCounter==this.children.size()){
                    this.state=AggregationState.WAITING_BROADCAST;
                    AggregationMessage sentMess=this.createMessage();
                    if(parent==null){
                        this.global=this.aggregate;
                        for(Finger child:this.children){
                            getPeer().sendMessage(child.getNetworkAddress(), sentMess);
                        }
                        this.state=AggregationState.COMPLETE;
                    }
                    else{
                        getPeer().sendMessage(this.parent.getNetworkAddress(), sentMess);
                    }
                }
                break;
            case WAITING_BROADCAST:
                this.global=receivedMess.aggregate;
                for(Finger child:this.children){
                    getPeer().sendMessage(child.getNetworkAddress(), receivedMess);
                }
                this.state=AggregationState.COMPLETE;
                break;
            case COMPLETE:
                logger.debug("Peer is not responding: State Complete.");
                break;
            default:
                logger.debug("Unrecognised state.");
                //sth else...
        }
    }

    /**
     * Creates an <code>AggregationMessage</code> by updating the aggregate and
     * adding it to the message.
     *
     * @return the aggregation message with the aggregate value.
    */
    private AggregationMessage createMessage(){
        AggregationMessage message=new AggregationMessage();
        this.aggregate+=this.value;
        message.aggregate=this.aggregate;
        return message;
    }

    /**
     * Handles incoming messages of type <code>AggregationMessage</code>.
     *
     * @param message the incoming message.
    */
    @Override
    public void handleIncomingMessage(Message message) {
        if (message instanceof AggregationMessage) {
                this.runPassiveState((AggregationMessage) message);
        }
    }

}
