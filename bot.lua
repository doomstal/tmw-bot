function packet_handler(...)
    local args = table.pack(...)
    local p = "";
    for _,v in ipairs(args) do
        if type(v) == "boolean" then
            if v then v = "[true]" else v = "[false]" end
        end
        if v == nil then v = "[nil]" end
        if type(v) == "table" then v = "[table]" end
        p = p .. v .. " ";
    end
    print(p)
    if args[1] == "being_name" and beings[ args[2] ].name == "doomstal" then
        leader = beings[args[2]]
        print("leader found! ", args[2])
        --send_packet("talk", "hello!")
    end
    if args[1] == "whisper" then
        local nick = args[2]
        local msg = args[3]
        if nick == "doomstal" then
            do_command(msg)
        elseif nick ~= "Server" then
            send_packet("whisper", nick, "hello, i am bot being developed by doomstal")
        end
    end

--[[
    if args[1] == "being_name" and beings[ args[2] ].name == "Ferry Schedule" then
        send_packet("npc_talk", args[2])
    end
    if args[1] == "npc_choise" then
        npcId = args[2]
        npcMenu = args[3]
        for k,v in pairs(npcMenu) do
            print(k .. "." ..  v)
        end
        send_packet("npc_choise", npcId, 2);
    end
    if args[1] == "npc_close" then
        npcId = args[2]
        send_packet("npc_close", npcId);
    end
]]
end

--tick = 0

function loop_body()
--    tick = tick + 1
    return true
end

function do_command(cmd)
    print("cmd>"..cmd)
    if cmd == "walk" then
        if leader then
            send_packet("walk", leader.x, leader.y)
        end
    end
    if cmd == "equipment" then
        for i, item in pairs(equipment) do
            local str = "[" .. i .. "] equip "
            for k,v in pairs(item) do
                str = str .. k .. "=" .. v .. " "
            end
            print(str)
        end
    end
    if cmd == "inventory" then
        for i, item in pairs(inventory) do
            local str = "[" .. i .. "] equip "
            for k,v in pairs(item) do
                str = str .. k .. "=" .. v .. " "
            end
            print(str)
        end
    end
    if cmd == "storage" then
        for i, item in pairs(storage) do
            local str = "[" .. i .. "] equip "
            for k,v in pairs(item) do
                str = str .. k .. "=" .. v .. " "
            end
            print(str)
        end
    end
end
