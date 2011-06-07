// $Id$
/*
 * Copyright (C) 2010, 2011 sk89q <http://www.sk89q.com>; purpleposeidon@gmail.com
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see <http://www.gnu.org/licenses/>.
 */

package com.sk89q.craftbook.ic;

import org.bukkit.Server;
import org.bukkit.block.Sign;

/**
 * Sends pulses to an ordinary IC. The pulses are sent to pin 0.
 * The other pins should still be accessible...
 * Minimum rate is 1.
 * 
 * Example usage:
 * icManager.register("MC0111", new SelfTriggerWrapper(server, new WirelessReceiver.Factory(server, true)), familySISO);
 * 
 * @author purpleposeidon
 *
 */

public class SelfTriggerWrapper extends AbstractICFactory {
    private final AbstractICFactory icFactory;
    private final int fireRate;

    public SelfTriggerWrapper(Server server, AbstractICFactory wrappedFactory) {
        super(server);
        icFactory = wrappedFactory;
        fireRate = 2;
    }

    public SelfTriggerWrapper(Server server, AbstractICFactory wrappedFactory, int rate) {
        super(server);
        icFactory = wrappedFactory;
        fireRate = rate;
        assert(rate >= 1);
    }

    @Override
    public IC create(Sign sign) {
        return new SelfTriggeredICWrapper(getServer(), sign, icFactory, fireRate);
    }

    @Override
    public void verify(Sign sign) throws ICVerificationException {
        icFactory.verify(sign);
    }



    private class SelfTriggeredICWrapper extends AbstractIC implements SelfTriggeredIC {

        private final AbstractICFactory icFactory;
        private final String title;
        private String signTitle;
        private final int fireRate;
        private int fireCount;

        public SelfTriggeredICWrapper(Server server, Sign sign, AbstractICFactory wrappedFactory, int rate) {
            super(server, sign);
            icFactory = wrappedFactory;
            IC testIC = icFactory.create(sign);
            title = "Self Triggered " + testIC.getTitle();
            signTitle = "ST " + testIC.getSignTitle();
            if (signTitle.length() > 15) {
                signTitle = signTitle.subSequence(0, 15).toString();
            }

            fireRate = rate;
            fireCount = 0;
        }

        @Override
        public String getTitle() {
            return title;
        }

        @Override
        public String getSignTitle() {
            return signTitle;
        }



        @Override
        public void think(ChipState chip) {
            fireCount++;
            boolean turningOn;
            if (fireCount % fireRate == 0) {
                turningOn = true;
            } else if (fireCount % fireRate == 1) {
                turningOn = false;
            } else {
                return;
            }

            try {
                icFactory.verify(getSign()); //This verify is probably unnecessary
            }
            catch (ICVerificationException e) {
                return;
            }
            IC ic = icFactory.create(getSign());
            if (fireRate == 1) {
                ic.trigger(new ChipStateFake(chip, true));
                ic.trigger(new ChipStateFake(chip, false));
            }
            else {
                ic.trigger(new ChipStateFake(chip, turningOn));
            }
            //TODO: Maybe not block pin 0?
        }

        @Override
        public boolean isActive() {
            return true;
        }

        @Override
        public void trigger(ChipState chip) {}

    }

    private class ChipStateFake implements ChipState {
        private final ChipState real;
        private final boolean isFired;
        ChipStateFake(ChipState realChip, boolean fire) {
            real = realChip;
            isFired = fire;
        }
        @Override
        public boolean get(int pin) {
            if (pin == 0) {
                return isFired;
            }
            return real.get(pin);
        }
        @Override
        public boolean getInput(int inputIndex) {
            if (inputIndex == 0) {
                return isFired;
            }
            return real.getInput(inputIndex);
        }
        @Override
        public boolean isValid(int pin) {
            if (pin != 0) {
                return real.isValid(pin);
            }
            return true;
        }
        @Override
        public boolean isTriggered(int pin) {
            if (pin == 0) {
                return isFired;
            }
            return real.isTriggered(pin);
        }
        //Java question: Is there a way to do without these?
        @Override
        public int getInputCount() { return real.getInputCount(); }
        @Override
        public boolean getOutput(int outputIndex) { return real.getOutput(outputIndex); }
        @Override
        public int getOutputCount() { return real.getOutputCount(); }
        @Override
        public void set(int pin, boolean value) { real.set(pin, value); }
        @Override
        public void setOutput(int outputIndex, boolean value) { real.setOutput(outputIndex, value); }
    }
}
