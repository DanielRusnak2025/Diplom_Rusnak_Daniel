<!-- CSV has locale-comma bug in tokens_per_second, fixed in 16c788d -->
# Model Comparison — 2026-11-03

## Configuration

- **Ollama version:** 0.30.3
- **Models:**

  | Model | Size |
  |---|---|
  | qwen3:8b | 5,2 GB |
  | qwen3:14b | 9,3 GB |
  | llama3.2 | 2,0 GB |

- **Articles:** 63 fetched, 8 failed to fetch (71 total URLs from queue)
- **Warmup:** 1 call per model with fixed dummy text (excluded from statistics)

## Statistics per model

| Metric | qwen3:8b | qwen3:14b | llama3.2 |
|---|---|---|---|
| p50 latency (ms) | 19821 | 41689 | 8154 |
| p95 latency (ms) | 25562 | 52935 | 9998 |
| % invalid (1st attempt) | 0,0% | 0,0% | 0,0% |
| % invalid (after retry) | 0,0% | 0,0% | 0,0% |
| % timeouts | 0,0% | 0,0% | 0,0% |
| % is_article=true | 55,6% | 52,4% | 63,5% |
| p50 tokens/s | 4,7 | 2,2 | 11,5 |
| think=false supported | yes | yes | yes |

## Category distribution

| Category | qwen3:8b | qwen3:14b | llama3.2 |
|---|---|---|---|
| Politics & Government | 9 | 10 | 9 |
| International Relations | 18 | 6 | 2 |
| Conflicts & Security | 7 | 16 | 25 |
| Energy | 1 | 1 | 0 |
| Business & Industry | 0 | 0 | 3 |
| Economy & Finance | 0 | 0 | 1 |

## Pairwise agreement

| Models | is_article match | main_category match |
|---|---|---|
| qwen3:8b vs qwen3:14b | 96% (61/63) | 63% (21/33) |
| qwen3:8b vs llama3.2 | 92% (58/63) | 40% (14/35) |
| qwen3:14b vs llama3.2 | 88% (56/63) | 66% (22/33) |

## Per-article results

| Domain | Title | Lang | qwen3:8b | qwen3:14b | llama3.2 |
|---|---|---|---|---|---|
| www.spiegel.de | (S+) Iran-Krieg: So läuft die doppelte Blockade der Straße von Hormus | de | not-article | not-article | not-article |
| newsletter-tracking.dw.com |  | en | not-article | not-article | not-article |
| www.bbc.com | BBC Home \| Breaking News, World News, US News, Sports, Business, Innovation, Climate, Culture, Travel, Video & Audio | en | Politics & Government/Elections | Politics & Government/Government & legislation | Politics & Government/Elections |
| www.theguardian.com | US and Iran in indirect talks to extend two-week ceasefire | en | International Relations/Peace talks & ceasefires | Conflicts & Security/General | International Relations/Diplomacy & summits |
| www.spiegel.de | (S+) Work-Life-Balance: Wie Sie trotz 40-Stunden-Woche Zeit für sich finden | de | not-article | not-article | not-article |
| newsletter-tracking.dw.com |  | en | not-article | not-article | not-article |
| www.bbc.com | BBC Home \| Breaking News, World News, US News, Sports, Business, Innovation, Climate, Culture, Travel, Video & Audio | en | Politics & Government/Elections | Politics & Government/General | Politics & Government/Elections |
| us18.campaign-archive.com | Today's most read articles on Haaretz | en | International Relations/Peace talks & ceasefires | Conflicts & Security/General | Politics & Government/General |
| www.theguardian.com | US extends waiver allowing countries to buy Russian oil – as it happened | en | International Relations/Diplomacy & summits | International Relations/Alliances & international organizations | Conflicts & Security/General |
| view.angebote.spiegel.de |  | en | not-article | not-article | not-article |
| newsmaker.md | (ВИДЕО) Политпредставители Кишинева и Тирасполя обменялись резкими репликами во время встречи «1+1» | ru | International Relations/Diplomacy & summits | Conflicts & Security/Civil wars & insurgencies | Conflicts & Security/Interstate wars |
| www.spiegel.de | Iran öffnet Straße von Hormus, USA bleiben bei Blockade | de | International Relations/Diplomacy & summits | not-article | Conflicts & Security/General |
| www.spiegel.de | Meinung: News des Tages: Iran gibt Straße von Hormus frei, AfD überholt CDU, Waldrama vor Poel | de | International Relations/Diplomacy & summits | not-article | Conflicts & Security/General |
| view.angebote.spiegel.de |  | en | not-article | not-article | not-article |
| newsletter-tracking.dw.com |  | en | not-article | not-article | not-article |
| newsletter-tracking.dw.com |  | en | not-article | not-article | not-article |
| www.dw.com | Война в Украине | ru | Conflicts & Security/Civil wars & insurgencies | Conflicts & Security/Military & defense | Conflicts & Security/Military & defense |
| www.dw.com | News and current affairs from Germany and around the world | en | Conflicts & Security/Civil wars & insurgencies | Conflicts & Security/Interstate wars | Conflicts & Security/General |
| us18.campaign-archive.com | The state of Israel: Trump says Israel ‘PROHIBITED’ from bombing Lebanon as cease-fire takes effect | en | Conflicts & Security/Civil wars & insurgencies | Conflicts & Security/General | Conflicts & Security/Interstate wars |
| view.angebote.spiegel.de |  | en | not-article | not-article | not-article |
| www.spiegel.de | Iran droht mit Schließung der Straße von Hormus – Trump mit einem Ende der Waffenruhe | de | International Relations/Diplomacy & summits | International Relations/Diplomacy & summits | Conflicts & Security/General |
| www.spiegel.de | (S+) David gegen Goliath: Ein Krebsmediziner legt sich mit einem globalen Pharmariesen an | de | not-article | not-article | Business & Industry/Companies & earnings |
| newsmaker.md | Зарплаты растут быстрее экономики. Что еще происходит с экономикой Молдовы?  | ru | not-article | not-article | not-article |
| www.spiegel.de | Iran schließt die Straße von Hormus erneut | de | International Relations/Diplomacy & summits | Conflicts & Security/General | Conflicts & Security/General |
| email.useinsider.com | 404 - Page Not Found | en | not-article | not-article | not-article |
| www.spiegel.de | Iran, USA und die Waffenruhe: Teheran sieht weiter Differenzen mit Washington | de | International Relations/Peace talks & ceasefires | International Relations/Peace talks & ceasefires | Conflicts & Security/General |
| www.spiegel.de | Iran, USA und die Waffenruhe: Teheran sieht weiter Differenzen mit Washington | de | International Relations/Peace talks & ceasefires | International Relations/Peace talks & ceasefires | Conflicts & Security/General |
| us18.campaign-archive.com | Inside the Israel-Hezbollah truce in Lebanon and the gaps that could unravel it | en | International Relations/Peace talks & ceasefires | Conflicts & Security/Civil wars & insurgencies | Conflicts & Security/Interstate wars |
| www.spiegel.de | Iran-Krieg: Donald Trump kündigt weitere Verhandlungen in Pakistan an | de | International Relations/Diplomacy & summits | International Relations/Diplomacy & summits | Conflicts & Security/General |
| email.useinsider.com | 404 - Page Not Found | en | not-article | not-article | not-article |
| newsletter-tracking.dw.com |  | en | not-article | not-article | not-article |
| www.spiegel.de | Irankrieg: Teheran droht mit Vergeltung für US-Beschuss von Frachtschiff | de | Conflicts & Security/Civil wars & insurgencies | Conflicts & Security/Interstate wars | Conflicts & Security/General |
| www.theguardian.com | US military seizes Iran-flagged ship trying to pass strait of Hormuz blockade | en | Conflicts & Security/Interstate wars | Conflicts & Security/Interstate wars | Conflicts & Security/Interstate wars |
| www.spiegel.de | Irankrieg: Teheran droht mit Vergeltung für US-Beschuss von Frachtschiff | de | Conflicts & Security/Civil wars & insurgencies | Conflicts & Security/Interstate wars | Conflicts & Security/General |
| www.bbc.com | BBC Home \| Breaking News, World News, US News, Sports, Business, Innovation, Climate, Culture, Travel, Video & Audio | en | Politics & Government/Elections | Politics & Government/General | Politics & Government/Elections |
| www.bbc.com | BBC News - Breaking news, video and the latest top stories from the U.S. and around the world | en | International Relations/Diplomacy & summits | Conflicts & Security/Civil wars & insurgencies | Conflicts & Security/General |
| email.useinsider.com | 404 - Page Not Found | en | not-article | not-article | not-article |
| www.theguardian.com | Virginia voters approve new congressional maps in blow to Trump | en | Politics & Government/Parties & leaders | Politics & Government/Government & legislation | Politics & Government/Elections |
| newsmaker.md | Еврокомиссар Марта Кос оценила прогресс Молдовы на пути в ЕС | ru | International Relations/Diplomacy & summits | Politics & Government/Government & legislation | Politics & Government/General |
| mailchi.mp | NM Espresso 22 апреля: чем в Молдове заменят ЧП, что будет с налогом на недвижимость, и ждут ли Румынию новые выборы | ro | not-article | not-article | not-article |
| newsmaker.md | В Молдове ЧП в энергетике могут заменить на режим повышенной готовности | ru | Energy/Energy prices & supply routes | Energy/Energy prices & supply routes | Conflicts & Security/General |
| www.theguardian.com | Kim Jong-un offers Putin ‘full support’ in Russia’s ‘sacred fight’ with west | en | Politics & Government/Parties & leaders | International Relations/Alliances & international organizations | International Relations/Diplomacy & summits |
| www.theguardian.com | ‘Impossible’ to reopen strait of Hormuz amid ‘flagrant’ ceasefire breaches, Iran says | en | International Relations/Diplomacy & summits | Conflicts & Security/Interstate wars | Conflicts & Security/General |
| www.spiegel.de | (S+) Irankrieg als Scheidungsgrund: Tucker Carlson rechnet mit Donald Trump ab | de | not-article | not-article | not-article |
| www.spiegel.de | (S+) Schwedt-Raffinerie: Das wären die Folgen einer russischen Ölblockade | de | not-article | not-article | Business & Industry/Companies & earnings |
| www.theguardian.com | Trump claims US has total control over strait of Hormuz after Iran seizes two container ships | en | Conflicts & Security/Interstate wars | Conflicts & Security/Interstate wars | Conflicts & Security/General |
| www.spiegel.de | (S+) Marine-Staatssekretär John Phlean: Donald Trump schwärmte von ihm – dennoch muss er nun gehen | de | not-article | not-article | Business & Industry/Companies & earnings |
| newsmaker.md | Глава полиции о требовании оппозиции уйти в отставку: «Подчинюсь, если решение примет мое руководство» | ru | Politics & Government/Parties & leaders | Politics & Government/Parties & leaders | Politics & Government/Government & legislation |
| newsmaker.md | Драгалин поздравила бывших коллег из прокуратуры после приговора Плахотнюку: «Я знаю изнутри, сколько труда за этим стоит» | ru | not-article | not-article | not-article |
| newsmaker.md | ЧП в цифрах. Что происходит в энергетике Молдовы после кризиса? | ru | not-article | not-article | not-article |
| email.useinsider.com | 404 - Page Not Found | en | not-article | not-article | not-article |
| email.useinsider.com | 404 - Page Not Found | en | not-article | not-article | not-article |
| www.spiegel.de | (S+) Haushalt 2027: Abgaben auf Zucker, Plastik, Tabak – so will die Regierung Milliarden reinholen | de | not-article | not-article | Economy & Finance/Public finance & taxes |
| www.spiegel.de | (S+) Warum die Rede von König Charles im US-Kongress ein diplomatisches Meisterstück war - die Analyse | de | not-article | not-article | Politics & Government/General |
| www.theguardian.com | Trump calls Iran’s response to peace plan ‘totally unacceptable’ as ceasefire frays | en | International Relations/Diplomacy & summits | Conflicts & Security/General | Conflicts & Security/General |
| www.theguardian.com | Democrats express ‘grave concerns’ over secretive ICE deportation flights | en | Politics & Government/Parties & leaders | Politics & Government/Government & legislation | Conflicts & Security/Terrorism |
| www.theguardian.com | Trump says ceasefire with Iran on ‘life support’ after rejecting peace proposals | en | International Relations/Diplomacy & summits | Conflicts & Security/Interstate wars | Conflicts & Security/General |
| newsmaker.md | Фуртунэ через суд требует признать «захват власти правящей партией». Реакция PAS | ru | Politics & Government/Parties & leaders | Politics & Government/Parties & leaders | Conflicts & Security/Terrorism |
| newsmaker.md | Гросу не планирует уходить с поста председателя PAS: Я баллотируюсь на новый срок | ru | not-article | not-article | not-article |
| www.theguardian.com | Vostochny cosmodrome: the remote Russian spaceport hosting Kim and Putin | en | International Relations/Diplomacy & summits | Politics & Government/Government & legislation | Conflicts & Security/General |
| www.bbc.com | US politics - BBC News | en | Politics & Government/Elections | Politics & Government/Elections | Politics & Government/Elections |
| www.spiegel.de | (S+) US-Präsident in China: Symbolik schlägt Substanz – fünf Gipfelerkenntnisse | de | not-article | not-article | not-article |
| view.angebote.spiegel.de |  | en | not-article | not-article | not-article |
