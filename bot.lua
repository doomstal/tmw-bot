function packet_handler(...)
    local args = table.pack(...)
    local p = "";
    for _,v in ipairs(args) do
        p = p .. v .. " ";
    end
    print(p)
end

function loop_body()
    return true
end
