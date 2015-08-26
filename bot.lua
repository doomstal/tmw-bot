state = "leader_wait"

leader_name = "doomstal"
leader_id = -1
leader = nil

inside_koga = false
interacting_npc = nil

trade_confirm_me = false
trade_confirm_other = false

koga_npcs = {}
koga_npcs["Candor Koga"] = 1
koga_npcs["Hurnscald South Koga"] = 1
koga_npcs["Hurnscald North Koga"] = 1
koga_npcs["Nivalis Koga"] = 1
koga_npcs["Tulimshar Koga"] = 1

koga_emblems = {}
koga_emblems[410] = "tulimshar"
koga_emblems[412] = "hurnscald"
koga_emblems[414] = "nivalis"
koga_emblems[416] = "candor"

koga_maps = {}
koga_maps["035-2"] = { exitx = 39, exity = 29 }
koga_maps["036-2"] = { exitx = 39, exity = 29 }

state_stack = {
    push = function(self, st)
        self[#self+1] = st
    end,
    pop = function(self)
        local st = self[#self]
        self[#self] = nil
        return st
    end,
    head = function(self)
        return self[#self]
    end
}

function loop_body()
    leader = beings[leader_id]
    if not leader then
        leader = find_being_name(leader_name)
        if leader then leader_id = leader.id end
    end

    if koga_maps[map_name] then inside_koga = true else inside_koga = false end

    if state == "leader_wait" then
        if leader then
            wait_time = nil
            state = "leader_follow"
            leader_x = leader.dstx
            leader_y = leader.dsty
            return
        end
        if not wait_time then wait_time = client_time + 5000 end
        if character.action ~= "sit" and wait_time < client_time then
            send_packet("action", "sit")
            character.action = "sit"
        end
    elseif state == "leader_follow" then
        if not leader and leader_x and leader_y and (character.x ~= leader_x or character.y ~= leader_y) then
            walk(leader_x, leader_y) -- leader's last known position
            return
        elseif not leader then -- leader could warp to other map or use ferry
            if inside_koga then
                local koga = koga_maps[map_name]
                if character.x==koga.exitx and math.abs(character.y-koga.exity)<3 then
                    walk(koga.exitx-1, koga.exity)
                end
                state_stack:push("leader_wait")
                state = "koga_disembarking"
                return
            end

            local warp, warp_dist = nearest_warp(leader_x, leader_y)
            if warp and warp_dist > 5 then warp = nil end

            local koga, koga_dist = nearest_koga(leader_x, leader_y)
            if koga and koga_dist > 10 then koga = nil end

            if not warp and not koga then
                state = "leader_wait"
                wait_time = nil
                return
            end

            local target = nil
            if warp and koga then
                if warp_dist < koga_dist then target = warp
                else target = koga end
            elseif warp then
                target = warp
            else
                target = koga
            end

            if target == warp then
                walk(warp.src_x, warp.src_y)
                state = "leader_wait"
                wait_time = nil
                return
            end
            if target == koga then
                state_stack:push("leader_wait")
                state = "koga_boarding"
                interacting_npc = koga.id
                send_packet("npc_talk", koga.id)
                return
            end

            return
        end
        leader_x = leader.dstx
        leader_y = leader.dsty

        if leader.action == "stand" then -- walking also fits here
            -- calculate target position to be one cell from leader
            local targetx = leader.dstx
            local targety = leader.dsty
            local dx = 0
            local dy = 0
            if character.x < leader.dstx then dx = -1
            elseif character.x > leader.dstx then dx = 1 end
            if character.y < leader.dsty then dy = -1
            elseif character.y > leader.dsty then dy = 1 end
            if not map_accesible(character.x, character.y, targetx + dx, targety + dy) then
                dx = 0
                if not map_accesible(character.x, character.y, targetx + dx, targety + dy) then dy = 0 end
            end
            targetx = targetx + dx
            targety = targety + dy
            local dist = math.max(math.abs(character.x-targetx),math.abs(character.y-targety))
            if dist > 0 then
                walk(targetx, targety)
            elseif character.action == "sit" then
                send_packet("action", "stand")
                character.action = "stand"
            end
        elseif leader.action == "sit" then
            -- calculate target position to be cell next to leader
            local targetx = leader.dstx
            local targety = leader.dsty
            if leader.dir == 4 or leader.dir == 1 then -- up or down
                if character.x > leader.dstx then targetx = leader.dstx+1
                else targetx = leader.dstx-1 end
                if not map_accesible(character.x, character.y, targetx, targety) then
                    if map_accesible(character.x, character.y, leader.dstx + 1, targety) then targetx = leader.dstx + 1
                    elseif map_accesible(character.x, character.y, leader.dstx - 1, targety) then targetx = leader.dstx - 1
                    else targetx = leader.dstx
                    end
                end
            elseif leader.dir == 8 or leader.dir == 2 then -- left or right
                if character.y > leader.dsty then targety = leader.dsty+1
                else targety = leader.dsty-1 end
                if not map_accesible(character.x, character.y, targetx, targety) then
                    if map_accesible(character.x, character.y, targetx, leader.dsty + 1) then targety = leader.dsty + 1
                    elseif map_accesible(character.x, character.y, targetx, leader.dsty - 1) then targety = leader.dsty - 1
                    else targety = leader.dsty
                    end
                end
            end
            local dist = math.max(math.abs(character.x-targetx),math.abs(character.y-targety))
--            print(character.x, targetx, character.y, targety)
            if character.action ~= "sit" and dist > 0 then
                walk(targetx, targety)
            elseif character.action ~= "sit" then
                send_packet("action", "sit")
                character.action = "sit"
            else
                -- just for fun :)
                local targetdir = leader.dir
                local dir_opposite = false
                if character.x == leader.dstx and (leader.dir == 4 or leader.dir == 1) then dir_opposite = true end
                if character.y == leader.dsty and (leader.dir == 8 or leader.dir == 2) then dir_opposite = true end
                if dir_opposite then
                    if leader.dir == 4 then targetdir = 1
                    elseif leader.dir == 1 then targetdir = 4
                    elseif leader.dir == 8 then targetdir = 2
                    elseif leader.dir == 2 then targetdir = 8 end
                end
                if character.dir ~= targetdir then
                    send_packet("turn", targetdir)
                    character.dir = targetdir
                end
            end
        end
    elseif state == "koga_boarding" then
        if inside_koga then
            state = state_stack:pop()
            interacting_npc = nil
        end
    elseif state == "koga_disembarking" then
        if inside_koga then
            local koga = koga_maps[map_name]
            walk(koga.exitx, koga.exity)
        else
            state = state_stack:pop()
        end
    end
    return true
end

function packet_handler(...)
    local args = table.pack(...)
    local p = "packet_handler ";
    for _,v in ipairs(args) do
        if type(v) == "boolean" then
            if v then v = "[true]" else v = "[false]" end
        end
        if v == nil then v = "[nil]" end
        if type(v) == "table" then v = "[table]" end
        p = p .. v .. " ";
    end
    print(p)

    if args[1] == "whisper" then
        if args[2] == leader_name then leader_command(args[3]) end
    elseif args[1] == "player_warp" then
        leader = nil
        wait_time = nil
    elseif args[1] == "npc_choise" then
        if state == "koga_boarding" and args[2] == interacting_npc then
            send_packet("npc_choise", args[2], 1)
            return
        end
        if state == "koga_disembarking" then
            send_packet("npc_choise", args[2], 1)
            return
        end
        if args[2] == interacting_npc then
            for k,v in pairs(args[3]) do
                print(k,v)
            end
            return
        end
        send_packet("npc_choise", args[2], #args[3]) -- last dialog variant usually ends conversation
    elseif args[1] == "npc_next" then
        send_packet("npc_next", args[2])
    elseif args[1] == "npc_close" then
        send_packet("npc_close", args[2])
    elseif args[1] == "trade_request" then
        if args[2] == leader_name then
            send_packet("trade_response", true)
        else
            send_packet("trade_response", false)
        end
        trade_confirm_me = false
        trade_confirm_other = false
    elseif args[1] == "trade_confirm" then
        if args[2] == 0 then
            trade_confirm_me = true
        elseif args[2] == 1 then
            trade_confirm_other = true
            send_packet("trade_confirm")
        end
        if trade_confirm_me and trade_confirm_other then
            send_packet("trade_finish")
        end
    end
end

----------------------------------------

walk_time = client_time
function walk(x, y)
    if character.action == "dead" then return end
--    print(character.dstx, x, character.dsty, y)
    if character.dstx==x and character.dsty==y then return end
--    print(character.x, character.dstx, character.y, character.dsty)
    if character.x~=character.dstx or character.y~=character.dsty then return end
--    print(client_time, walk_time)
    if client_time < walk_time then return end

    local dir = character.dir
    if y < character.y then
        dir = 4;
    elseif y > character.y then
        dir = 1;
    else
        if x < character.x then
            dir = 8;
        elseif x > character.x then
            dir = 2;
        end
    end

    walk_time = client_time + 200 -- to prevent kick for packet spamming
    send_packet("walk", x, y, 0)
    character.action = "stand"
end

function find_being_name(name)
    for id, being in pairs(beings) do
        if being.name == name then
            return being
        end
    end
end

function nearest_warp(x, y)
    if not warps[map_name] then return end
    local ret = nil
    local ret_dist = nil;
    for _, warp in pairs(warps[map_name]) do
        if not x or not y then return warp end
        local dist = math.sqrt((x-warp.src_x)^2+(y-warp.src_y)^2)
        if not ret or dist < ret_dist then
            ret = warp
            ret_dist = dist
        end
    end
    return ret, ret_dist
end

function nearest_koga(x, y)
    local ret = nil
    local ret_dist = nil
    for _, being in pairs(beings) do
        if koga_npcs[being.name] then
            if not x or not y then return being end
            local dist = math.sqrt((x-being.x)^2+(y-being.y)^2)
            if not ret or dist < ret_dist then
                ret = being
                ret_dist = dist
            end
        end
    end
    return ret, ret_dist
end

function leader_command(cmd)
    cmd = mysplit(cmd)
    if cmd[1] == "reload" then
        send_packet("reload")
    elseif cmd[1] == "send_packet" then
        if cmd[2] == "talk" then
            local str = cmd[3]
            for i=4,#cmd do str = str.." "..cmd[i] end
            cmd[3] = str
        elseif cmd[2] == "npc_talk" then
            if tonumber(cmd[3])==nil then
                myjoin(cmd, 3)
                local being = find_being_name(cmd[3])
                if not being then return end
                cmd[3] = being.id
                interacting_npc = being.id
            else
                interacting_npc = cmd[3]
            end
        elseif cmd[2] == "npc_choise" and interacting_npc then
            if not cmd[4] then
                cmd[4] = cmd[3]
                cmd[3] = interacting_npc
            end
        elseif cmd[2] == "npc_close" and interacting_npc then
            cmd[3] = interacting_npc
            interacting_npc = nil
        end

        local args = {}
        for i=2,#cmd do
            if tonumber(cmd[i]) ~= nil then
                args[#args+1] = tonumber(cmd[i])
            else
                args[#args+1] = cmd[i]
            end
        end
        send_packet(table.unpack(args))
    elseif cmd[1] == "call_func" then
        local f = _G[cmd[2]]
        if type(f) ~= "function" then return end
        for i=3,#cmd do
            if tonumber(cmd[i]) ~= nil then cmd[i] = tonumber(cmd[i]) end
        end
        f(table.unpack(cmd, 3))
    elseif cmd[1] == "print" then
        local var = cmd[2]
        local t = _G[var]
        if cmd[3]~=nil then
            var = var.."."..cmd[3]
            t = t[cmd[3]]
        end
        if not t then return end
        if type(t) == "boolean" then
            if t then t = "[true]" else v = "[false]" end
        elseif type(t) == "function" then
            t = "[function]"
        end
        if type(t) ~= "table" then
            print(var.." = "..t)
            return
        end
        print(var.." = [table]")
        for k,v in pairs(t) do
            if v==nil then
                v = "[nil]"
            elseif type(v)=="boolean" then
                if v then v = "[true]" else v = "[false]" end
            elseif type(v)=="function" then
                v = "[function]"
            elseif type(v)=="table" then
                local t2 = v
                v = "{"
                local first = true
                for k2,v2 in pairs(t2) do
                    if type(v2)=="function" then v2 = "[function]" end
                    if type(v2)=="table" then v2 = "[table]" end
                    if type(v2)=="boolean" then if v2 then v2 = "[true]" else v2 = "[false]" end end
                    if first then first = false else k2 = ", "..k2 end
                    v = v..k2.." = "..v2
                end
                v = v.."}"
            end
            print(k.." = "..v)
        end
    elseif cmd[1] == "wait" then
        if state == "leader_follow" then
            state = "leader_wait_passive"
            send_packet("action", "sit")
            send_packet("turn", 1)
        end
    elseif cmd[1] == "follow" then
        if state == "leader_wait_passive" then
            state = "leader_wait"
        end
    end
end

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

function myjoin(array, from) -- shortens array
    if not from then from = 1 end
    if from > #array then return end
    local str = array[#array]
    array[#array] = nil
    for i = #array, from, -1 do
        str = array[i].." "..str
        array[i] = nil
    end
    array[from] = str
end