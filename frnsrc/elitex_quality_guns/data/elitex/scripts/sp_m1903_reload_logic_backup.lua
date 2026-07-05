function M.tick_reload(api)

    local param = api:getScriptParams()
    local reload_feed, reload_cooldown = getReloadTimingFromParam(param)

    if reload_feed == nil then
        return NOT_RELOADING, -1
    end

    local ammo = api:getAmmoCount()
    local hasBullet = api:hasBulletInBarrel()
    local totalAmmo = ammo + (hasBullet and 1 or 0)

    -- ✅ PREVENT FULL MAG RELOAD CRASH
    if totalAmmo >= 5 then
        return NOT_RELOADING, -1
    end

    -- map ammo → index (1–5)
    local level = totalAmmo + 1

    local progressTime = api:getReloadTime()

    local feed_time = reload_feed[level]
    local finish_time = reload_cooldown[level]

    local stateType = NOT_RELOADING
    local countDown = -1
    local oldStateType = api:getReloadStateType()

    if progressTime < feed_time then
        stateType = TACTICAL_RELOAD_FEEDING
        countDown = feed_time - progressTime
    elseif progressTime < finish_time then
        stateType = TACTICAL_RELOAD_FINISHING
        countDown = finish_time - progressTime
    else
        stateType = NOT_RELOADING
        countDown = -1
    end

    -- ✅ THIS WAS MISSING → REQUIRED
    if oldStateType == TACTICAL_RELOAD_FEEDING and oldStateType ~= stateType then
        local needAmmoCount = api:getNeededAmmoAmount()

        if api:isReloadingNeedConsumeAmmo() then
            api:putAmmoInMagazine(api:consumeAmmoFromPlayer(needAmmoCount))
        else
            api:putAmmoInMagazine(needAmmoCount)
        end
    end

    return stateType, countDown
end