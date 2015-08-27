state = "leader_wait"

leader_name = "doomstal"
leader_id = -1
leader = nil

inside_koga = false
interacting_npc = nil
koga_destination = nil

trade_confirm_me = false
trade_confirm_other = false

route = nil
route_destination = nil

locations = {}
locations["candor"] = { map = "029-1", x = 45, y = 95 }
locations["candor bank"] = { map = "029-2", x = 32, y = 121 }
locations["hurnscald"] = { map = "009-1", x = 53, y = 31 }
locations["hurnscald store"] = { map = "009-2", x = 18, y = 103 }
locations["graveyard inn"] = { map = "027-2", x = 30, y = 29 }

local candor_ferry_destinations = {
    { map = "029-1", x = 46 , y = 109 },
    { map = "008-1", x = 137, y = 75 }
}
local world_ferry_destinations = {
    { map = "008-1", x = 137 , y = 64 },
    { map = "031-1", x = 95 , y = 109 },
    { map = "001-1", x = 63 , y = 73 }
}
koga_npcs = {}
koga_npcs["Candor Koga"] = { name = "Candor Koga", map = "029-1", x = 53, y = 112, destinations = candor_ferry_destinations }
koga_npcs["Hurnscald South Koga"] = { name = "Hurnscald South Koga", map = "008-1", x = 141, y = 76, destinations = candor_ferry_destinations }
koga_npcs["Hurnscald North Koga"] = { name = "Hurnscald North Koga", map = "008-1", x = 141, y = 65, destinations = world_ferry_destinations }
koga_npcs["Nivalis Koga"] = { name = "Nivalis Koga", map = "031-1", x = 107, y = 110, destinations = world_ferry_destinations }
koga_npcs["Tulimshar Koga"] = { name = "Tulimshar Koga", map = "001-1", x = 76, y = 73, destinations = world_ferry_destinations }

koga_emblems = {}
koga_emblems["001-1"] = { job = 410, map = "001-1" } -- tulimshar
koga_emblems["008-1"] = { job = 412, map = "008-1" } -- hurnscald
koga_emblems["031-1"] = { job = 414, map = "031-1" } -- nivalis
koga_emblems["029-1"] = { job = 416, map = "029-1" } -- candor

koga_maps = {}
koga_maps["035-2"] = { map = "035-2", sitx = 37, sity = 27, exitx = 39, exity = 29, destinations = world_ferry_destinations }
koga_maps["036-2"] = { map = "036-2", sitx = 37, sity = 27, exitx = 39, exity = 29, destinations = candor_ferry_destinations }

if warps then
    local cwarps = warps["029-2"] -- theese are not in the warp list
    cwarps[#cwarps+1] = { src_map = "029-2", src_x = 44, src_y = 31, dst_map = "029-2", dst_x = 112, dst_y = 85 }
    cwarps[#cwarps+1] = { src_map = "029-2", src_x = 114, src_y = 93, dst_map = "029-1", dst_x = 32, dst_y = 100 }
    for _,wrps in pairs(warps) do
        for _, warp in pairs(wrps) do
            warp.src_region = map_region(warp.src_map, warp.src_x, warp.src_y)
            warp.dst_region = map_region(warp.dst_map, warp.dst_x, warp.dst_y)
        end
    end
end

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

old_state = nil

function loop_body()
    if state ~= old_state then
        print("new_state", state)
        local str = "state_stack ="
        for _,v in ipairs(state_stack) do str = str.." "..v end
        print(str)
        old_state = state
    end

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
            send_packet("turn", 1)
            send_packet("action", "sit")
            character.action = "sit"
        end
    elseif state == "leader_follow" then
        if not leader then -- leader could warp to other map or use ferry
            if leader_x and leader_y then
                if (character.x ~= leader_x or character.y ~= leader_y) then
                    walk(leader_x, leader_y) -- leader's last known position
                    return
                else
                    leader_x = nil
                    leader_y = nil
                end
            end
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
            if not map_accessible(character.x, character.y, targetx + dx, targety + dy) then
                dx = 0
                if not map_accessible(character.x, character.y, targetx + dx, targety + dy) then dy = 0 end
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
                if not map_accessible(character.x, character.y, targetx, targety) then
                    if map_accessible(character.x, character.y, leader.dstx + 1, targety) then targetx = leader.dstx + 1
                    elseif map_accessible(character.x, character.y, leader.dstx - 1, targety) then targetx = leader.dstx - 1
                    else targetx = leader.dstx
                    end
                end
            elseif leader.dir == 8 or leader.dir == 2 then -- left or right
                if character.y > leader.dsty then targety = leader.dsty+1
                else targety = leader.dsty-1 end
                if not map_accessible(character.x, character.y, targetx, targety) then
                    if map_accessible(character.x, character.y, targetx, leader.dsty + 1) then targety = leader.dsty + 1
                    elseif map_accessible(character.x, character.y, targetx, leader.dsty - 1) then targety = leader.dsty - 1
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
    elseif state == "koga_inside" then
        if not inside_koga then print("!!! not inside_koga") return end
        if not koga_destination then print("not koga_destination") return end

        if find_being_job(koga_emblems[koga_destination].job) then
            state = "koga_disembarking"
            return
        end

        local koga_map = koga_maps[map_name]
        local targetx = koga_map.sitx
        local targety = koga_map.sity
        if character.x ~= targetx or character.y ~= targety then
            walk(targetx, targety)
        else
            if character.action ~= "sit" then
                send_packet("turn", 1)
                send_packet("action", "sit")
                character.action = "sit"
            end
        end
    elseif state == "koga_disembarking" then
        if inside_koga then
            local koga = koga_maps[map_name]
            walk(koga.exitx, koga.exity)
        else
            koga_destination = nil
            state = state_stack:pop()
        end
    elseif state == "goto" then
        if route and #route > 0 then
            local region = route[#route]
            local targetx, targety
            if region.map ~= map_name or region.region ~= map_region(map_name, character.x, character.y) then
                if region.warp then
                    if region.warp.src_map ~= map_name then print("!!! region.warp.src_map ~= map_name") return end
                    targetx = region.warp.src_x
                    targety = region.warp.src_y
                elseif region.koga then
                    if region.koga.map ~= map_name then print("!!! region.koga.map ~= map_name") return end
                    targetx = region.koga.x
                    targety = region.koga.y
                elseif region.koga_map then
                    if region.koga_map.map ~= map_name then print("!!! region.koga_map.map ~= map_name") return end
                    koga_destination = region.map
                    state_stack:push(state)
                    state = "koga_inside"
                    return
                end
                if character.x ~= targetx or character.y ~= targety then
                    walk(targetx, targety)
                    return
                else
                    if region.koga then
                        local koga = find_being_name(region.koga.name)
                        if koga then
                            koga_destination = region.map
                            state_stack:push(state)
                            state_stack:push("koga_inside")
                            state = "koga_boarding"
                            interacting_npc = koga.id
                            send_packet("npc_talk", koga.id)
                            return
                        else
                            if character.action ~= "sit" then
                                send_packet("turn", 1)
                                send_packet("action", "sit")
                                character.action = "sit"
                            end
                            return
                        end
                    end
                end
            else
                route[#route] = nil
                if route[#route] then
                    print("next region", route[#route].map)
                end
                return
            end
        else
            if map_name ~= route_destination.map then print("!!! map_name ~= route_destination.map") return end
            if not route_destination.region then route_destination.region = map_region(route_destination.map, route_destination.x, route_destination.y) end
            if map_region(map_name, character.x, character.y) ~= route_destination.region then print("!!! map_region ~= route_destination.region") return end
            local targetx = route_destination.x
            local targety = route_destination.y
            if character.x ~= targetx or character.y ~= targety then
                walk(targetx, targety)
            else
                state = state_stack:pop()
            end
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
        leader_x = nil
        leader_y = nil
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

    if math.max(math.abs(character.x-x),math.abs(character.y-y)) > 10 then
        local path = map_find_path(character.x, character.y, x, y)
        if path and #path > 10 then
            x = path[10].x
            y = path[10].y
        end
        if not path then
            print("not path", character.x, character.y, character.dstx, character.dsty, x, y)
        end
    end

    if character.dstx==x and character.dsty==y then return end
--    print(character.x, character.dstx, character.y, character.dsty)
--    if character.x~=character.dstx or character.y~=character.dsty then return end
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

function find_being_job(job)
    for id, being in pairs(beings) do
        if being.job == job then
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
    elseif cmd[1] == "goto" then
--        if state ~= "leader_follow" and state ~= "leader_wait" and state ~= "leader_wait_passive" then return end
        myjoin(cmd, 2)
        local dest = locations[cmd[2]]
        if not dest then return end

        print("destination", dest.map, map_region(dest.map, dest.x, dest.y), dest.x, dest.y)
        route = find_route({map = map_name, x = character.x, y = character.y}, dest)
        if route and #route > 0 then
            print("*** route")
            for _,r in pairs(route) do
                if r.warp then print(r.map, r.region, r.x, r.y, "warp", r.warp.src_map, r.warp.src_region, r.warp.src_x, r.warp.src_y, "to", r.warp.dst_map, r.warp.dst_region, r.warp.dst_x, r.warp.dst_y) end
                if r.koga then print(r.map, r.region, r.x, r.y, "koga", r.koga.name, r.koga.map, r.koga.x, r.koga.y) end
            end
            print("*** route")
            print("current", map_name, map_region(map_name, character.x, character.y), character.x, character.y)
--            if true then return end
            walk(character.x, character.y)
            route_destination = dest
            state_stack:push("leader_wait")
            state = "goto"
        end
    end
end

--[[
    start_region = map, [x, y] or [region]
    location = map, [x, y] or [region]
    returns sequence of { map, [warp] [koga] [koga_map] } - maps to location in reverse order
]]
function find_route(start_region, location)
    if not location.region then location.region = map_region(location.map, location.x, location.y) end
    local location_tag = location.map.."-"..location.region
--    print("location_tag", location_tag)

    local viewed_regions = {}
    local regions_to_view = { }

    if not start_region.region then start_region.region = map_region(start_region.map, start_region.x, start_region.y) end
    local start_region_tag = start_region.map.."-"..start_region.region

    viewed_regions[start_region_tag] = 1;
    regions_to_view[1] = start_region
    local i_start = 1
    local i_end = 2;

    local region = nil
    while i_start < i_end do
        region = regions_to_view[i_start]
        i_start = i_start + 1

        local region_tag = region.map.."-"..region.region
--        print("region_tag", region_tag)
        if region_tag == location_tag then break end

        if region.x and region.y then
            table.sort(warps[region.map], function(a,b)
                return (a.src_x-region.x)^2+(a.src_y-region.y)^2 < (b.src_x-region.x)^2+(a.src_y-region.y)
            end)
        end
        for _, warp in pairs(warps[region.map]) do
--            print("warp src region", map_region(warp.src_map, warp.src_x, warp.src_y))
            if warp.src_map == region.map and map_region(warp.src_map, warp.src_x, warp.src_y) == region.region then
                local new_region = {
                    map = warp.dst_map,
                    x = warp.dst_x,
                    y = warp.dst_y,
                    region = map_region(warp.dst_map, warp.dst_x, warp.dst_y),
                    warp = warp,
                    prev = region
                }
                local new_region_tag = new_region.map.."-"..new_region.region
--                print("new_region_tag", new_region_tag)

                if not viewed_regions[new_region_tag] then
                    viewed_regions[new_region_tag] = 1;
                    regions_to_view[i_end] = new_region
                    i_end = i_end + 1
                end
            end
        end
        for name, koga in pairs(koga_npcs) do
            if koga.map == region.map and map_region(koga.map, koga.x, koga.y) == region.region then
                for _, dest in pairs(koga.destinations) do
                    local new_region = {
                        map = dest.map,
                        x = dest.x,
                        y = dest.y,
                        region = map_region(dest.map, dest.x, dest.y),
                        koga = koga,
                        prev = region
                    }
                    local new_region_tag = new_region.map.."-"..new_region.region

                    if not viewed_regions[new_region_tag] then
                        viewed_regions[new_region_tag] = 1;
                        regions_to_view[i_end] = new_region
                        i_end = i_end + 1
                    end
                end
            end
        end
        if koga_maps[region.map] then
            local koga_map = koga_maps[region.map]
            for _, dest in pairs(koga_map.destinations) do
                local new_region = {
                    map = dest.map,
                    x = dest.x,
                    y = dest.y,
                    region = map_region(dest.map, dest.x, dest.y),
                    koga_map = koga_map,
                    prev = region
                }
                local new_region_tag = new_region.map.."-"..new_region.region

                if not viewed_regions[new_region_tag] then
                    viewed_regions[new_region_tag] = 1;
                    regions_to_view[i_end] = new_region
                    i_end = i_end + 1
                end
            end
        end
    end

    local route = {}
    while region do
        route[#route+1] = region
        region = region.prev
    end
    return route
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