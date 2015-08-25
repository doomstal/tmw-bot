task = {}

leader_name = "doomstal"
leader = nil
leader_id = -1

leader_last_seen_x = nil
leader_last_seen_y = nil

state_using_ferry = false

function mysplit(inputstr, sep)
    if sep == nil then
        sep = "%s"
    end
    local t={} ; i=1
    for str in string.gmatch(inputstr, "([^"..sep.."]+)") do
        t[i] = str
        i = i + 1
    end
    return t
end

function find_leader()
    for id, being in pairs(beings) do
        if being.name == leader_name then
            leader = being
            leader_id = being.id
            print("!!! leader found !!!")
            break
        end
    end
end

koga_names = {}
koga_names["Candor Koga"] = 1
koga_names["Hurnscald South Koga"] = 1
koga_names["Hurnscald North Koga"] = 1
koga_names["Nivalis Koga"] = 1
koga_names["Tulimshar Koga"] = 1

koga_emblems = {}
koga_emblems[410] = "tulimshar"
koga_emblems[412] = "hurnscald"
koga_emblems[414] = "nivalis"
koga_emblems[416] = "candor"

koga_maps = {}
koga_maps["035-2"] = { sitx = 37, sity = 27, exitx = 39, exity = 29 }
koga_maps["036-2"] = { sitx = 37, sity = 27, exitx = 39, exity = 29 }

function find_koga(x, y)
    local ret = nil
    for id, being in pairs(beings) do
        if koga_names[being.name] then
            if ret == nil or Math.min(x-being.x, y-being.y) < Math.min(ret.x-being.x, ret.y-being.y) then
                ret = being
                if not x or not y then break end
            end
        end
    end
    return ret
end

function nearest_warp(x, y)
    local ret = nil
    for _, warp in pairs(warps) do
        if ret == nil or (x-warp.src_x)^2+(y-warp.src_y)^2 < (ret.src_x-warp.src_x)^2+(ret.src_y-warp.src_y)^2 then
            ret = warp
            if not x or not y then break end
        end
    end
    if ret and x and y and (x-ret.src_x)^2+(y-ret.src_y)^2 > 9 then return nil end
    return ret
end

task.follow_leader = {
    init = function(self)
    end,
    tick = function(self)
        if koga_maps[map_name] then
            run_task(task.use_ferry)
            return
        end
        leader = beings[leader_id]
        if not leader then
            if leader_last_seen_x then
                local warp = nearest_warp(leader_last_seen_x, leader_last_seen_y)
                if warp and (character.dstx ~= warp.src_x or character.src_y ~= warp.src_y) then
                    send_packet("walk", warp.src_x, warp.src_y)
                    return
                end
                if find_koga() then
                    run_task(task.use_ferry)
                    return
                end
            end
        else
            if math.abs(character.dstx - leader.dstx) > 1 or math.abs(character.dsty - leader.dsty) > 1 then
                send_packet("walk", leader.dstx, leader.dsty);
                character.dstx = leader.dstx;
                character.dsty = leader.dsty;
            end
        end
    end,
    packet_handler = function(self, args)
        if args[1] == "being_action" and args[2] == leader_id then
            if args[4] == "sit" then
                send_packet("turn", 1)
                send_packet("action", "sit")
            end
        end
    end
}

task.use_ferry = {
    init = function(self, destination)
        state_using_ferry = true
        if destination then self.destinataion = destination end
        self.state = "outside"
        self.inside = false
        self.leader_was_inside = false;
    end,
    tick = function(self)
        if self.state ~= self.old_state then
            print("new state =", self.state)
            self.old_state = self.state
        end
        if leader and self.inside then self.leader_was_inside = true end
        if self.state == "inside_sit" and not self.destination then
            if not leader and self.leader_was_inside then
                self.state = "disembark"
                return
            end
        end
        if self.state == "outside" then
            if koga_maps[map_name] then
                self.state = "inside"
                self.inside = true
                return
            end
            local koga = find_koga(leader_last_seen_x, leader_last_seen_y)
            if koga then
                send_packet("npc_talk", koga.id)
                self.state = "outside_talk"
                return
            end
        end
        if self.state == "outside_close" and koga_maps[map_name] then
            self.state = "inside"
            self.inside = true
        end
        if self.state == "inside" then
            local koga = koga_maps[map_name]
            send_packet("walk", koga.sitx, koga.sity)
            self.state = "inside_walk"
            return
        end
        if self.state == "inside_walk" then
            local koga = koga_maps[map_name]
            if character.x == koga.sitx and character.y == koga.sity then
                send_packet("turn", 1)
                send_packet("action", "sit")
                self.state = "inside_sit"
                return
            elseif character.dstx ~= koga.sitx and character.dsty ~= koga.sity then
                send_packet("walk", koga.sitx, koga.sity)
            end
        end
        if self.state == "disembark" then
            local koga = koga_maps[map_name]
            send_packet("walk", koga.exitx, koga.exity);
            self.state = "disembark_in_progress"
            return
        end
        if self.state == "disembark_close" and not koga_maps[map_name] then
            self.state = nil
            self.finished = true
            state_using_ferry = false
            return
        end
    end,
    packet_handler = function(self, args)
        if args[1] == "npc_choise" then
            local name = beings[args[2]].name
            if koga_names[name] and self.state == "outside_talk" then
                send_packet("npc_choise", args[2], 1)
                for k,v in pairs(args[3]) do print(k,v) end
                self.state = "outside_choise"
                return
            end
            if self.state == "disembark_in_progress" then
                send_packet("npc_choise", args[2], 1)
                self.state = "disembark_requested"
            else
                send_packet("npc_choise", args[2], 2)
            end
        elseif args[1] == "npc_close" then
            send_packet("npc_close", args[2])
            if self.state == "outside_choise" then
                self.state = "outside_close"
            elseif self.state == "disembark_requested" then
                self.state = "disembark_close"
            end
        elseif args[1] == "player_warp" then
            print("*** player_warp ***")
        elseif args[1] == "being_name" then
            local job = beings[args[2]].job
            if koga_names[job] then self.koga_position = koga_names[job] end
        end
    end
}

task.leader_commands = {
    tick = function(self)
        if leader then
            leader_last_seen_x = leader.dstx
            leader_last_seen_y = leader.dsty
        end
        leader = beings[leader_id]
        if not leader then
            find_leader()
        end
    end,
    packet_handler = function(self, args)
        if args[1] == "whisper" and args[2] == leader_name then
            local cmds = mysplit(args[3])
            if cmds[1] == "leader" and leader then
                for k,v in pairs(leader) do
                    print(k, v)
                end
            elseif cmds[1] == "character" then
                for k,v in pairs(character) do
                    print(k, v)
                end
            elseif cmds[1] == "warps" then
                print("warp list:")
                for _,warp in pairs(warps) do
                    print(warp.src_map, warp.src_x, warp.src_y, warp.dst_map, warp.dst_x, warp.dst_y)
                end
            elseif cmds[1] == "walk" and cmds[2] and cmds[3] then
                send_packet("walk", tonumber(cmds[2]), tonumber(cmds[3]))
            elseif cmds[1] == "come" and leader then
                send_packet("walk", leader.x, leader.y)
            elseif cmds[1] == "turn" and cmds[2] then
                send_packet("turn", cmds[2])
            end
        end
    end
}

task.npc_listener = {
    packet_handler = function(self, args)
        if args[1] == "npc_choise" then
            local name = beings[args[2]].name
            if koga_names[name] then
                if not state_using_ferry then
--                    send_packet("npc_close", args[2])
                    send_packet("npc_choise", args[2], 2)
                end
            end
        end
    end
}

