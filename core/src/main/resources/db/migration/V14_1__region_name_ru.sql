ALTER TABLE region ADD COLUMN name_ru VARCHAR(100) NULL;

UPDATE region SET name_ru = CASE region_key
    WHEN 'north_america'       THEN 'Северная Америка'
    WHEN 'latin_america'       THEN 'Латинская Америка'
    WHEN 'western_europe'      THEN 'Западная Европа'
    WHEN 'northern_europe'     THEN 'Северная Европа'
    WHEN 'southern_europe'     THEN 'Южная Европа'
    WHEN 'eastern_europe'      THEN 'Восточная Европа'
    WHEN 'russia_central_asia' THEN 'Россия и Центральная Азия'
    WHEN 'middle_east'         THEN 'Ближний Восток'
    WHEN 'africa'              THEN 'Африка'
    WHEN 'south_asia'          THEN 'Южная Азия'
    WHEN 'east_southeast_asia' THEN 'Восточная и Юго-Восточная Азия'
    WHEN 'global'              THEN 'Весь мир'
    ELSE NULL
END;
