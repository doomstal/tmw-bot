function packet_handler(...)
    local args = table.pack(...)
    local p = "";
    for _,v in ipairs(args) do
        p = p .. v .. " ";
    end
    print(p)
    if args[1] == "being_name" and beings[args[2]].name == "doomstal" then
        leader = beings[args[2]]
        print("leader found! ", args[2])
    end
end

tick = 0

function loop_body()
    tick = tick + 1
    if tick > 100 then
        tick = 0
        if leader then
            send_packet("walk", leader.x, leader.y)
        end
    end
    return true
end
