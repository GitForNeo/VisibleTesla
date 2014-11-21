/*
 * StreamProducer.java - Copyright(c) 2014 Joe Pasqua
 * Provided under the MIT License. See the LICENSE file for details.
 * Created: Apr 27, 2014
 */
package org.noroomattheinn.visibletesla;

import org.noroomattheinn.tesla.StreamState;
import org.noroomattheinn.tesla.Streamer;

/**
 * StreamProducer: Generate a stream of locations on demand.
 *
 * @author Joe Pasqua <joe at NoRoomAtTheInn dot org>
 */
public class StreamProducer extends Executor<StreamProducer.Request>
                            implements ThreadManager.Stoppable {
    
/*------------------------------------------------------------------------------
 *
 * Constants and Enums
 * 
 *----------------------------------------------------------------------------*/
    
    private static final long StreamingThreshold = 400;     // 0.4 seconds

/*------------------------------------------------------------------------------
 *
 * Internal State
 * 
 *----------------------------------------------------------------------------*/
    
    private final  Streamer streamer;
    private        long     lastSnapshotTime = 0;
    
/*==============================================================================
 * -------                                                               -------
 * -------              Public Interface To This Class                   ------- 
 * -------                                                               -------
 *============================================================================*/
    
    public StreamProducer(AppContext ac) {
        super(ac, "StreamProducer");
        this.streamer = appContext.vehicle.getStreamer();
        ac.tm.addStoppable((ThreadManager.Stoppable)this);
    }
    
    public void produce(boolean stream) {
        super.produce(new Request(stream, false));
    }
    
    @Override public void stop() { if (streamer != null) { streamer.forceClose(); } }
    
/*------------------------------------------------------------------------------
 *
 * Internal Methods - Some declared protected since they implement interfaces
 * 
 *----------------------------------------------------------------------------*/

    @Override protected boolean execRequest(Request r) throws Exception {
        StreamState snapshot = r.continuation ? streamer.tryExistingStream() :
                                                streamer.beginStreamIfNeeded();
        if (snapshot == null) {
            if (r.stream && r.continuation && isInMotion()) { produce(true); }
            return r.continuation;  // Null is ok on a continuation, not otherwise
        }
        
        if (!r.continuation || snapshot.timestamp - lastSnapshotTime > StreamingThreshold) {
            lastSnapshotTime = snapshot.timestamp;
            appContext.lastKnownStreamState.set(snapshot);
        }
        
        if (r.stream) super.produce(new Request(r.stream, true));
        return true;
    }
    
    private boolean isInMotion() {
        return appContext.lastKnownStreamState.get().isInMotion();
    }

    @Override protected void addToHistogram(Executor.Request r) {
        if (!((Request)r).continuation) super.addToHistogram(r);
    }
    
    public static class Request extends Executor.Request {
        public final boolean stream;
        public final boolean continuation;
        
        Request(boolean stream, boolean continuation) {
            super(null);
            this.stream = stream;
            this.continuation = continuation;
        }
        
        @Override protected String getRequestName() { return "Stream"; }
        @Override protected int maxRetries() { return 2; }
    }
}