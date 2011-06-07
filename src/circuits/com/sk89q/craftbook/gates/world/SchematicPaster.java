// $Id$
/*
 * Copyright (C) 2010, 2011 sk89q <http://www.sk89q.com>
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

package com.sk89q.craftbook.gates.world;

import java.io.File;
import java.io.IOException;
import java.util.Comparator;
import java.util.HashMap;
import java.util.PriorityQueue;

import org.bukkit.Location;
import org.bukkit.Server;
import org.bukkit.block.Sign;

import com.sk89q.craftbook.ic.AbstractIC;
import com.sk89q.craftbook.ic.AbstractICFactory;
import com.sk89q.craftbook.ic.ChipState;
import com.sk89q.craftbook.ic.IC;
import com.sk89q.craftbook.ic.ICVerificationException;
import com.sk89q.craftbook.ic.RestrictedIC;
import com.sk89q.craftbook.util.SignUtil;
import com.sk89q.worldedit.CuboidClipboard;
import com.sk89q.worldedit.EditSession;
import com.sk89q.worldedit.FilenameException;
import com.sk89q.worldedit.LocalPlayer;
import com.sk89q.worldedit.MaxChangedBlocksException;
import com.sk89q.worldedit.Vector;
import com.sk89q.worldedit.WorldEdit;
import com.sk89q.worldedit.bukkit.BukkitWorld;
import com.sk89q.worldedit.bukkit.WorldEditPlugin;
import com.sk89q.worldedit.data.DataException;

public class SchematicPaster extends AbstractIC {

    protected boolean risingEdge;

    public SchematicPaster(Server server, Sign sign, boolean risingEdge) {
        super(server, sign);
        this.risingEdge = risingEdge;

    }

    @Override
    public String getTitle() {
        return "Schematic Paster";
    }

    @Override
    public String getSignTitle() {
        return "SCHEMA PASTE";
    }

    private CuboidClipboard getCuboid() throws ICVerificationException, IOException, DataException {
        return SchematicCacher.get(getServer(), getSign().getLine(2));
    }

    @Override
    public void trigger(ChipState chip) {
        if (!(risingEdge && chip.getInput(0) || (!risingEdge && !chip.getInput(0)))) {
            return;
        }
        Location L = SignUtil.getBackBlock(getSign().getBlock()).getLocation();
        Vector loc = new Vector(L.getBlockX(), L.getBlockY()+1, L.getBlockZ());

        String line3 = getSign().getLine(3).toUpperCase();
        boolean pasteAtOrigin = false;
        if (line3.length() > 0 && line3.charAt(0) == '0') {
            pasteAtOrigin = true;
            line3 = line3.substring(1).trim();
        }


        CuboidClipboard cb;
        try {
            cb = getCuboid();
        } catch (Throwable t) {
            System.out.println("[MC1209] Couldn't get cuboid: " + t.toString());
            return;
        }

        EditSession editSession = new EditSession(new BukkitWorld(getSign().getWorld()), 500000);

        try {
            boolean noAir = (line3.compareToIgnoreCase("NOAIR") == 0);
            if (noAir || line3.length() == 0) {
                //Use old world edit method
                if (pasteAtOrigin) {
                    cb.place(editSession, cb.getOrigin(), noAir);
                }
                else {
                    cb.paste(editSession, loc, noAir);
                }
            }
            else {
                //Use new world edit paste with boolean ops
                //NOTE: Comment this out if your WorldEdit isn't awesome enough
                EditSession.BooleanOperation op;
                try {
                    op = EditSession.BooleanOperation.fromString(line3);
                }
                catch (IllegalArgumentException e) {
                    System.out.println("[MC1209] Invalid line 3: " + line3);
                    return;
                }
                if (pasteAtOrigin) {
                    cb.place(editSession, cb.getOrigin(), op);
                }
                else {
                    cb.paste(editSession, loc, op);
                }
            }
        }
        catch (MaxChangedBlocksException e) {
            //Does it undo?
            System.out.println("[MC1209] MaxChangedBlocksException");
            return;
        }
        System.out.println("[MC1209] Succesfully triggered " + getSign().getLine(3));
    }

    public static class Factory extends AbstractICFactory implements RestrictedIC {

        protected boolean risingEdge;

        public Factory(Server server, boolean risingEdge) {
            super(server);
            this.risingEdge = risingEdge;
        }

        @Override
        public IC create(Sign sign) {
            return new SchematicPaster(getServer(), sign, risingEdge);
        }

        @Override
        public void verify(Sign sign) throws ICVerificationException {
            SchematicCacher.get(getServer(), sign.getLine(2));
        }
    }

    static class SchematicCacher {
        static HashMap<String, CacheItem> cache;

        class CacheItem {
            int accessesCount;
            long loaded;
            String filename;
            CuboidClipboard cube;

            CacheItem(Server server, String name) throws ICVerificationException {
                loadCache(server, name);
            }

            void loadCache(Server server, String name) throws ICVerificationException {
                /*
                 * Syntax:
                 *  filename >
                 *  filename N
                 *  filename >NS<V
                 * 
                 * <> rotations
                 * NSEW^V flips
                 */
                accessesCount = 0;

                filename = name;
                String transforms = "";
                if (name.contains(" ")) {
                    String[] s = name.split(" ", 1);
                    filename = s[0];
                    transforms = s[1];
                }

                File file = getFile(server, filename);
                loaded = file.lastModified();
                try {
                    cube = CuboidClipboard.loadSchematic(file);
                } catch (DataException e) {
                    throw new ICVerificationException(e);
                } catch (IOException e) {
                    throw new ICVerificationException(e);
                }


                while (transforms.length() != 0) {
                    char c = transforms.toUpperCase().charAt(0);
                    transforms = transforms.substring(1);

                    switch (c) {
                    case '>':
                    case '<':
                        //do rotations
                        int rotations = 0;
                        while (name.charAt(0) == '>' || name.charAt(0) == '<') {
                            if (name.charAt(0) == '>') {
                                rotations++;
                            } else {
                                rotations--;
                            }
                            name = name.substring(1);
                        }
                        cube = new CacheItem(server, name).cube;
                        if (rotations != 0) {
                            rotations %= 4;
                            if (rotations < 4) {
                                rotations += 4;
                            }
                            cube.rotate2D(90*rotations);
                        }
                        break;
                    case 'N':
                    case 'S':
                    case 'E':
                    case 'W':
                    case '^':
                    case 'V':
                        CuboidClipboard.FlipDirection direction;
                        if (c == 'N' || c == 'S') {
                            direction = CuboidClipboard.FlipDirection.NORTH_SOUTH;
                        } else if (c == 'E' || c == 'W') {
                            direction = CuboidClipboard.FlipDirection.WEST_EAST;
                        } else {
                            direction = CuboidClipboard.FlipDirection.UP_DOWN;
                        }
                        name = name.substring(1);
                        cube.flip(direction);
                    }
                }

                SchematicCacher.add(this);
            }

            public File getFile(Server server, String filename) throws ICVerificationException {
                WorldEditPlugin wep = (WorldEditPlugin) server.getPluginManager().getPlugin("WorldEdit");
                WorldEdit we = wep.getWorldEdit();
                if (we == null) {
                    throw new ICVerificationException("This IC requires the WorldEdit plugin");
                }

                if (filename.length() == 0) {
                    throw new ICVerificationException("Filename not provided");
                }


                File dir = we.getWorkingDirectoryFile(we.getConfiguration().saveDir);
                File f;
                try {
                    //null argument is lame
                    f = we.getSafeOpenFile((LocalPlayer)null, dir, filename, "schematic", new String[] {"schematic"});
                } catch (FilenameException e) {
                    throw new ICVerificationException("Unable to open schematic: " + e.toString());
                }


                try {
                    String filePath = f.getCanonicalPath();
                    String dirPath = dir.getCanonicalPath();

                    if (!filePath.substring(0, dirPath.length()).equals(dirPath)) {
                        throw new ICVerificationException("Schematic could not read or it does not exist.");
                    } else {
                        return f;
                    }
                } catch (IOException e) {
                    throw new ICVerificationException("Schematic could not read or it does not exist: " + e.getMessage());
                }
            }

            void touch() {
                accessesCount++;
            }

            public void checkDirty(Server server, String name) throws ICVerificationException {
                if (getFile(server, filename).lastModified() > loaded) {
                    loadCache(server, name);
                }
            }
        } //CacheItem

        class CacheItemComparator implements Comparator<CacheItem> {
            public int compare(CacheItem a, CacheItem b) {
                return a.accessesCount - b.accessesCount;
            }
        }

        static void add(CacheItem item) {
            if (cache == null) {
                cache = new HashMap<String, CacheItem>();
            }
            cache.put(item.filename, item);
        }

        static CuboidClipboard get(Server server, String name) throws ICVerificationException {
            if (cache == null) {
                cache = new HashMap<String, CacheItem>();
            }
            CacheItem ret = cache.get(name);

            if (ret != null) {
                ret.checkDirty(server, name);
            }
            if (ret == null) {
                System.out.println("[MC1209] Loading " + name);
                ret = new SchematicCacher().new CacheItem(server, name);
            }
            ret.touch();
            return ret.cube;
        }

        void recycle() {
            int tripSize = 30, targetSize = 20;
            if (cache != null && cache.size() > tripSize) {
                PriorityQueue<CacheItem> items = new PriorityQueue<CacheItem>(cache.size(), new CacheItemComparator());
                items.addAll(cache.values());
                for (int i = cache.size(); i > targetSize; i--) {
                    cache.remove(items.remove().filename);
                }
            }
        }

    }
}


