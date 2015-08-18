function packet_handler(...)
    local args = table.pack(...)
    local p = "";
    for _,v in ipairs(args) do
        if type(v) == "boolean" then
            if v then v = "true" else v = "false" end
        end
        p = p .. v .. " ";
    end
    print(p)
    if args[1] == "being_name" and beings[args[2]].name == "doomstal" then
        leader = beings[args[2]]
        print("leader found! ", args[2])
        send_packet("talk", "hello!")
    end
    if args[1] == "whisper" then
        local nick = args[2]
        local msg = args[3]
        if nick ~= "Server" then
            send_packet("whisper", nick, "hello, i am bot being developed by doomstal")
        end
    end
end

tick = 0

function loop_body()
--    tick = tick + 1
--[[
    if tick > 200 then
        for i, item in pairs(equipment) do
            local str = "[" .. i .. "] equip "
            for k,v in pairs(item) do
                str = str .. k .. "=" .. v .. " "
            end
            print(str)
        end
        return false
    end
]]
--[[    if tick > 100 then
        tick = 0
        if leader then
            send_packet("walk", leader.x, leader.y)
        end
    end
]]
    return true
end
