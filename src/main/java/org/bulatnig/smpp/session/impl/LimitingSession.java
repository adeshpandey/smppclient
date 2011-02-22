package org.bulatnig.smpp.session.impl;

import org.bulatnig.smpp.pdu.CommandId;
import org.bulatnig.smpp.pdu.Pdu;
import org.bulatnig.smpp.pdu.PduException;
import org.bulatnig.smpp.session.Session;
import org.bulatnig.smpp.session.MessageListener;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

/**
 * Session, limiting the number of outgoing requests per second.
 *
 * @author Bulat Nigmatullin
 */
public class LimitingSession implements Session {

    private static final Logger logger = LoggerFactory.getLogger(LimitingSession.class);

    /**
     * Limit message count per second.
     */
    private static final int TIMEOUT = 1000;

    private final Session session;

    /**
     * Holds the times when the last messages was sent.
     */
    private final BlockingQueue<Long> sentTimes;

    public LimitingSession(Session session, int maxMessagesPerSecond) {
        this.session = session;
        sentTimes = new LinkedBlockingQueue<Long>(maxMessagesPerSecond);
        for (int i = 0; i < maxMessagesPerSecond; i++)
            sentTimes.add(0L);
    }

    @Override
    public void setMessageListener(MessageListener messageListener) {
        session.setMessageListener(messageListener);
    }

    @Override
    public void setSmscResponseTimeout(int timeout) {
        session.setSmscResponseTimeout(timeout);
    }

    @Override
    public void setPingTimeout(int timeout) {
        session.setPingTimeout(timeout);
    }

    @Override
    public Pdu open(Pdu pdu) throws PduException, IOException {
        return session.open(pdu);
    }

    @Override
    public long nextSequenceNumber() {
        return session.nextSequenceNumber();
    }

    @Override
    public void send(Pdu pdu) throws PduException, IOException {
        if (CommandId.SUBMIT_SM != pdu.getCommandId()) {
            session.send(pdu);
        } else {
            try {
                long timeToSleep = sentTimes.poll() + TIMEOUT - System.currentTimeMillis();
                logger.trace("Time spent from N message back to this: {}.", timeToSleep);
                if (timeToSleep > 0) {
                    logger.trace("Going to sleep {}.", timeToSleep);
                    Thread.sleep(timeToSleep);
                }
                session.send(pdu);
            } catch (InterruptedException e) {
                throw new IOException("Send interrupted.");
            } finally {
                sentTimes.add(System.currentTimeMillis());
            }
        }
    }

    @Override
    public void close() {
        session.close();
    }
}
