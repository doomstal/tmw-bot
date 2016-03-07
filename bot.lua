state = "leader_wait"

leader_id = -1
leader = nil

inside_koga = false
interacting_npc = nil
koga_destination = nil

trade_confirm_me = false
trade_confirm_other = false
trading_leader = false
trading_count = 0

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
koga_emblems["001-1"] = { race = 410, map = "001-1" } -- tulimshar
koga_emblems["008-1"] = { race = 412, map = "008-1" } -- hurnscald
koga_emblems["031-1"] = { race = 414, map = "031-1" } -- nivalis
koga_emblems["029-1"] = { race = 416, map = "029-1" } -- candor

koga_maps = {}
koga_maps["035-2"] = { map = "035-2", sit_x = 37, sit_y = 27, exit_x = 39, exit_y = 29, destinations = world_ferry_destinations }
koga_maps["036-2"] = { map = "036-2", sit_x = 37, sit_y = 27, exit_x = 39, exit_y = 29, destinations = candor_ferry_destinations }

if warps then
    local cwarps = warps["029-2"] -- theese are not in the warp list
    cwarps[#cwarps+1] = { map = "029-2", x = 44, y = 31, dst_map = "029-2", dst_x = 112, dst_y = 85 }
    cwarps[#cwarps+1] = { map = "029-2", x = 114, y = 93, dst_map = "029-1", dst_x = 32, dst_y = 100 }
    for _,wrps in pairs(warps) do
        for _, warp in pairs(wrps) do
            warp.rid = map_region(warp.map, warp.x, warp.y)
            warp.dst_rid = map_region(warp.dst_map, warp.dst_x, warp.dst_y)
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
    check_attribute_increase()
    if trading_leader then check_trade_add() end

    if state ~= old_state then
        str = "new_state = "..state.." | stack ="
        for _,v in ipairs(state_stack) do str = str.." "..v end
        print(str)
        old_state = state
        if state == "leader_wait" then
            leader_wait = nil
        elseif state == "koga_boarding" then
            if not interacting_npc then
                state = state:pop()
                return
            end
            send_packet("npc_talk", interacting_npc)
        elseif state == "goto" then
            if not route_destination then
                state = state:pop()
                return
            end

            if not route_destination.rid then route_destination.rid = map_region(route_destination.map, route_destination.x, route_destination.y) end
            print("destination", route_destination.map.."-"..route_destination.rid, route_destination.x, route_destination.y)
            route = find_route({map = map_name, x = character.x, y = character.y}, route_destination)
            if route and #route > 0 then
                print("*** route")
                for _,r in pairs(route) do
                    if r.warp then print(r.map.."-"..r.rid, r.x, r.y, "warp", r.warp.map, r.warp.rid, r.warp.x, r.warp.y, "to", r.warp.dst_map, r.warp.dst_rid, r.warp.dst_x, r.warp.dst_y) end
                    if r.koga then print(r.map.."-"..r.rid, r.x, r.y, "koga", r.koga.name, r.koga.map, r.koga.x, r.koga.y) end
                end
                print("*** route")
                print("current", map_name.."-"..map_region(map_name, character.x, character.y), character.x, character.y)
        --            if true then return end
                walk(character.x, character.y)
            else
                state = state:pop()
                return
            end
        end
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
            leader_x = leader.dst_x
            leader_y = leader.dst_y
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
                    return
                end
            end
            if inside_koga then
                local koga = koga_maps[map_name]
                if character.x==koga.exit_x and math.abs(character.y-koga.exit_y)<3 then
                    walk(koga.exit_x-1, koga.exit_y)
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
                walk(warp.x, warp.y)
                state = "leader_wait"
                return
            end
            if target == koga then
                state_stack:push("leader_wait")
                state = "koga_boarding"
                interacting_npc = koga.id
                return
            end

            return
        end
        leader_x = leader.dst_x
        leader_y = leader.dst_y

        if leader.action == "stand" then -- walking also fits here
            -- mob hunting
            local mob = nearest_enemy_mob(character.x, character.y, character.range)
            if mob then
                attack(mob)
            end

            -- calculate target position to be one cell from leader
            local targetx = leader.dst_x
            local targety = leader.dst_y
            local dx = 0
            local dy = 0
            if character.x < leader.dst_x then dx = -1
            elseif character.x > leader.dst_x then dx = 1 end
            if character.y < leader.dst_y then dy = -1
            elseif character.y > leader.dst_y then dy = 1 end
            if not map_accessible(character.x, character.y, targetx + dx, targety + dy) then
                dx = 0
                if not map_accessible(character.x, character.y, targetx + dx, targety + dy) then dy = 0 end
            end
            targetx = targetx + dx
            targety = targety + dy

            local drop, dist = nearest_safe_drop(character.x, character.y, 8)
            if drop then
                targetx = drop.x
                targety = drop.y
                if dist < 1 then
                    send_packet("pickup", drop.id)
                    drop.pickup = false
                end
            end

            if map_get_threat(targetx, targety) > 0 then
                if map_get_threat(character.x, character.y) == 0 then
                    targetx = character.x
                    targety = character.y
                else
                    local p = map_nearest_safe_spot(targetx, targety, 10);
                    if p then
                        targetx = p.x
                        targety = p.y
                    end
                end
            end
            local dist = math.max(math.abs(character.x-targetx),math.abs(character.y-targety))
            if dist > 0 then
                walk(targetx, targety)
            elseif character.action == "sit" then
                send_packet("action", "stand")
                character.action = "stand"
            end
        elseif leader.action == "sit" then
            -- calculate target position to be cell next to leader
            local targetx = leader.dst_x
            local targety = leader.dst_y
            if leader.dir == 4 or leader.dir == 1 then -- up or down
                if character.x > leader.dst_x then targetx = leader.dst_x+1
                else targetx = leader.dst_x-1 end
                if not map_accessible(character.x, character.y, targetx, targety) then
                    if map_accessible(character.x, character.y, leader.dst_x + 1, targety) then targetx = leader.dst_x + 1
                    elseif map_accessible(character.x, character.y, leader.dst_x - 1, targety) then targetx = leader.dst_x - 1
                    else targetx = leader.dst_x
                    end
                end
            elseif leader.dir == 8 or leader.dir == 2 then -- left or right
                if character.y > leader.dst_y then targety = leader.dst_y+1
                else targety = leader.dst_y-1 end
                if not map_accessible(character.x, character.y, targetx, targety) then
                    if map_accessible(character.x, character.y, targetx, leader.dst_y + 1) then targety = leader.dst_y + 1
                    elseif map_accessible(character.x, character.y, targetx, leader.dst_y - 1) then targety = leader.dst_y - 1
                    else targety = leader.dst_y
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
                if character.x == leader.dst_x and (leader.dir == 4 or leader.dir == 1) then dir_opposite = true end
                if character.y == leader.dst_y and (leader.dir == 8 or leader.dir == 2) then dir_opposite = true end
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
        if not koga_destination then print("!!! no koga_destination") return end

        if find_being_race(koga_emblems[koga_destination].race) then
            state = "koga_disembarking"
            return
        end

        local koga_map = koga_maps[map_name]
        local targetx = koga_map.sit_x
        local targety = koga_map.sit_y
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
            walk(koga.exit_x, koga.exit_y)
        else
            koga_destination = nil
            state = state_stack:pop()
        end
    elseif state == "goto" then
        if route and #route > 0 then
            local region = route[#route]
            local targetx, targety
            if region.map ~= map_name or region.rid ~= map_region(map_name, character.x, character.y) then
                if region.warp then
                    if region.warp.map ~= map_name then print("!!! region.warp.map ~= map_name") return end
                    targetx = region.warp.x
                    targety = region.warp.y
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
            if not route_destination.rid then route_destination.rid = map_region(route_destination.map, route_destination.x, route_destination.y) end
            if map_region(map_name, character.x, character.y) ~= route_destination.rid then print("!!! map_region ~= route_destination.rid") return end
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
--[[    local p = "packet_handler ";
    for _,v in ipairs(args) do
        if type(v) == "boolean" then
            if v then v = "[true]" else v = "[false]" end
        end
        if v == nil then v = "[nil]" end
        if type(v) == "table" then v = "[table]" end
        p = p .. v .. " ";
    end
    print(p)]]

    if args[1] == "whisper" then
        if args[2] == leader_name then
            leader_command(args[3])
        else
            print("[w] "..args[2].." : "..args[3])
        end
    elseif args[1] == "being_chat" then
        local id = args[2]
        if beings[id] and beings[id].name then print("[b] "..beings[id].name.." : "..args[3])
        else print("[b] ["..args[2].."] : "..args[3]) end
    elseif args[1] == "being_action" then
        if args[4] == "hit" or args[4] == "critical" or args[4] == "multi" or args[4] == "flee" then
--            print("being_action "..args[2].." "..args[3].." "..args[4].." "..args[5])
            if args[2] == leader_id and beings[args[3]] then
                beings[args[3]].enemy = true
            end
            if args[3] == character.id then
                print(character.hp - args[5], character.hp_max)
            end
            if args[2] == character.id then
                beings[args[3]].mydrop = true
            end
        end
--    elseif args[1] == "being_attack" then
--        print("being_attack "..args[2].." "..args[3].." "..args[4])
--        if args[2] == leader_id and beings[args[3]] then
--            beings[args[3]].enemy = true
--        end
    elseif args[1] == "being_selfeffect" then
        if args[2] == character.id then
            print("effect "..args[3])
            if args[3] == 3 then
                print(character.hp, character.hp_max)
            end
        end
    elseif args[1] == "being_remove" then
        local being = beings[args[2]]
        if being then
            if being.action == "dead" then
                being.death_time = client_time
            end
--            beings[args[2]] = nil
        end
    elseif args[1] == "being_update" then
        local being = beings[args[2]]
        if being then
            if being.death_time and client_time > being.death_time + 2000 then
                beings[args[2]] = nil
            end
        end
    elseif args[1] == "item_drop" then
        local drop = items[args[2]]
        if drop_is_mine(drop) then drop.pickup = true end
    elseif args[1] == "player_chat" then
        if args[2] then print("[!] "..args[3])
        else print("[g] "..args[3]) end
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
            trading_leader = true
            trading_count = 0
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
            trading_leader = false
        end
    end
end

----------------------------------------

attribute_sequence = {
    {name = "dex", value = 97},
    {name = "agi", value = 96},
    {name = "vit", value = 90}
}

increase_time = client_time
function check_attribute_increase()
    if increase_time > client_time then return end

    local to_increase = nil
    local level
    for _, attr in ipairs(attribute_sequence) do
        level = character[ attr.name .. "_base" ]
        local need = character[ attr.name .. "_need" ]
        if (not level) or (not need) then return end
        if level < attr.value then
            if need < character.char_points then
                to_increase = attr.name
            end
            break
        end
    end

    if not to_increase then return end

    increase_time = client_time + 200
    send_packet("increase_attribute", to_increase)
    print("increase_attribute "..to_increase.." to "..(level+1))
end

dont_trade = {}
dont_trade["Arrow"] = 3000
dont_trade["RedApple"] = 100

trade_add_time = client_time
function check_trade_add()
    if trading_count >= 10 then return end
    if trade_add_time > client_time then return end

    local index = nil
    local amount = nil

    for ind, item in pairs(inventory) do
        local amount_have = item.amount
        if trade_buy[item.id] then
            amount_have = amount_have + trade_buy[item.id].amount
        end
        if (not dont_trade[item.name] or amount_have > dont_trade[item.name]) and (not trade_sell[ind]) then
            index = ind
            amount = item.amount
            if dont_trade[item.name] then
                amount = amount_have - dont_trade[item.name]
            end
            if not amount then amount = 1 end
            trading_count = trading_count + 1
            print("selling "..item.name)
            break
        end
    end

    if not index then return end

    trade_add_time = client_time + 500
    send_packet("trade_add", index, amount)
end

attack_time = client_time
function attack(mob)
    if character.action == "dead" then return end

    if attack_time > client_time then return end

    attack_time = client_time + character.attack_speed
    send_packet("attack", mob.id)
    character.action = "stand"
end

walk_last_x = nil
walk_last_y = nil
walk_time = client_time
function walk(x, y)
    if character.action == "dead" then return end

    character.dst_x = x
    character.dst_y = y

--    print(character.x, character.y, x, y)
    local path = nil
    if math.abs(character.x - x) <= 10 and math.abs(character.y - y) <= 10 then
        path = map_bot_path(character.x, character.y, x, y)
    else
        path = map_find_path(character.x, character.y, x, y, true)
    end
    if path then
        character.path = path;
        character.path_index = 1;
        if #path > 1 then
            x = path[2].x
            y = path[2].y

--        local s = character.x .. ',' .. character.y .. ' > '
--        for _,p in ipairs(path) do
--            s = s .. p.x .. ',' .. p.y .. ' '
--        end
--        s = s .. ' > ' .. character.dst_x .. ',' .. character.dst_y
--        print(s)

        end
    end

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

    if client_time < walk_time then return end
    if x == walk_last_x and y == walk_last_y then return end

    walk_last_x = x
    walk_last_y = y
    walk_time = client_time + 150 -- to prevent kick for packet spamming
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

function find_being_race(race)
    for id, being in pairs(beings) do
        if being.race == race then
            return being
        end
    end
end

function drop_is_mine(drop)
    for _, being in pairs(beings) do
        if being.action == "dead" and being.mydrop then
            local dist = math.max(math.abs(drop.x - being.x), math.abs(drop.y - being.y))
            if dist < 2 then return true end
        end
    end
    return false
end

function nearest_being(x, y, typ)
    if not typ then typ = "monster" end
    local ret = nil
    local ret_dist = nil
    for _, being in pairs(beings) do
        if being.type == typ then
            local dist = math.sqrt((x-being.x)^2 + (y-being.y)^2)
            if not ret or dist < ret_dist then
                ret = being
                ret_dist = dist
            end
        end
    end
    return ret, ret_dist
end

function nearest_safe_drop(x, y, range)
    local ret = nil
    local ret_dist = nil

    for _, drop in pairs(items) do
        local dist = math.max(math.abs(drop.x - x), math.abs(drop.y - y))

        if dist < range and drop.pickup and map_get_threat_total(drop.x, drop.y) == 0 then
            if not ret_dist or dist < ret_dist then
                ret = drop
                ret_dist = dist
            end
        end
    end

    return ret, ret_dist
end

function nearest_enemy_mob(x, y, range)
    local ret = nil
    local ret_dist = nil

    for _, being in pairs(beings) do
        local dist = math.max(math.abs(being.x - x), math.abs(being.y - y))

        if dist < range and being.type == "monster" and being.action ~= "dead"
        and (being.enemy or mobDB[being.race].mode.aggressive) then
            if not ret_dist or dist < ret_dist then
                ret = being
                ret_dist = dist
            end
        end
    end
    return ret
end

function nearest_warp(x, y)
    if not warps[map_name] then return end
    local ret = nil
    local ret_dist = nil;
    for _, warp in pairs(warps[map_name]) do
        if not x or not y then return warp, 0 end
        local dist = math.sqrt((x-warp.x)^2+(y-warp.y)^2)
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
            if not x or not y then return being, 0 end
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
    for i=2,#cmd do
        if cmd[i] == "[true]" then cmd[i] = true end
        if cmd[i] == "[false]" then cmd[i] = false end
    end
    if cmd[1] == "reload" then
        send_packet("reload")
    elseif cmd[1] == "send_packet" then
        if cmd[2] == "talk" then
            myjoin(cmd, 3)
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
        elseif cmd[2] == "npc_buy_sell" and interacting_npc then
            if not cmd[4] then
                cmd[4] = cmd[3]
                cmd[3] = interacting_npc
            end
        elseif (cmd[2] == "npc_buy_item" or cmd[2] == "npc_sell_item") and interacting_npc then
            if not cmd[5] then
                cmd[5] = cmd[4]
                cmd[4] = cmd[3]
                cmd[3] = interacting_npc
            end
        elseif (cmd[2] == "npc_int_input" or cmd[2] == "npc_str_input") and interacting_npc then
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
        if state == "leader_follow" or state == "leader_wait" then
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

        route_destination = dest
        state_stack:push("leader_wait")
        state = "goto"
    elseif cmd[1] == "attack" then
        local being = nearest_being(character.x, character.y, "monster")
        if not being then return end
        send_packet("attack", being.id)
    end
end

--[[
    start_region = map, [x, y] or [region id]
    location = map, [x, y] or [region id]
    returns sequence of { map, [warp] [koga] [koga_map] } - maps to location in reverse order
]]
function find_route(start_region, location)
    if not location.rid then location.rid = map_region(location.map, location.x, location.y) end
    local location_tag = location.map.."-"..location.rid
--    print("location_tag", location_tag)

    local viewed_regions = {}
    local regions_to_view = { }

    if not start_region.rid then start_region.rid = map_region(start_region.map, start_region.x, start_region.y) end
    local start_region_tag = start_region.map.."-"..start_region.rid

    viewed_regions[start_region_tag] = 1;
    regions_to_view[1] = start_region
    local i_start = 1
    local i_end = 2;

    local region = nil
    while i_start < i_end do
        region = regions_to_view[i_start]
        i_start = i_start + 1

        local region_tag = region.map.."-"..region.rid
--        print("region_tag", region_tag)
        if region_tag == location_tag then break end

        if region.x and region.y then
            table.sort(warps[region.map], function(a,b)
                return (a.x-region.x)^2+(a.y-region.y)^2 < (b.x-region.x)^2+(b.y-region.y)^2
            end)
        end
        for _, warp in pairs(warps[region.map]) do
--            print("warp src region", map_region(warp.map, warp.x, warp.y))
            if warp.map == region.map and warp.rid == region.rid then
                local new_region = {
                    map = warp.dst_map,
                    x = warp.dst_x,
                    y = warp.dst_y,
                    rid = warp.dst_rid,
                    warp = warp,
                    prev = region
                }
                local new_region_tag = new_region.map.."-"..new_region.rid
--                print("new_region_tag", new_region_tag)

                if not viewed_regions[new_region_tag] then
                    viewed_regions[new_region_tag] = 1;
                    regions_to_view[i_end] = new_region
                    i_end = i_end + 1
                end
            end
        end
        for name, koga in pairs(koga_npcs) do
            if not koga.rid then koga.rid = map_region(koga.map, koga.x, koga.y) end
            if koga.map == region.map and koga.rid == region.rid then
                for _, dest in pairs(koga.destinations) do
                    if not dest.rid then dest.rid = map_region(dest.map, dest.x, dest.y) end
                    local new_region = {
                        map = dest.map,
                        x = dest.x,
                        y = dest.y,
                        rid = dest.rid,
                        koga = koga,
                        prev = region
                    }
                    local new_region_tag = new_region.map.."-"..new_region.rid

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
                if not dest.rid then dest.rid = map_region(dest.map, dest.x, dest.y) end
                local new_region = {
                    map = dest.map,
                    x = dest.x,
                    y = dest.y,
                    rid = dest.rid,
                    koga_map = koga_map,
                    prev = region
                }
                local new_region_tag = new_region.map.."-"..new_region.rid

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