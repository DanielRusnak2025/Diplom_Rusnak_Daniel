# Model Comparison — 2026-11-04

## Configuration

- **Ollama version:** 0.30.3
- **Models:**

  | Model | Size |
  |---|---|
  | qwen3:8b | 5,2 GB |

- **Articles:** 62 fetched, 9 failed to fetch, 18 pre-filtered (no AI call) (71 total URLs from queue)
- **Warmup:** 1 call per model with fixed dummy text (excluded from statistics)

## Statistics per model

| Metric | qwen3:8b |
|---|---|
| p50 latency (ms) | 23571 |
| p95 latency (ms) | 26161 |
| % invalid (1st attempt) | 0.0% |
| % invalid (after retry) | 0.0% |
| % timeouts | 0.0% |
| % is_article=true | 90.9% |
| % subtopic substituted | 5.0% (2 articles) |
| p50 tokens/s | 4.6 |
| think=false supported | yes |

## Category distribution

| Category | qwen3:8b |
|---|---|
| International Relations | 22 |
| Society | 1 |
| Conflicts & Security | 5 |
| Health & Medicine | 1 |
| Economy & Finance | 3 |
| Politics & Government | 7 |
| Energy | 1 |

## Pairwise agreement

*(requires ≥ 2 models)*

## Per-article results

| Domain | Title | Lang | text_chars | extract_source | skip_reason | og_type | qwen3:8b |
|---|---|---|---|---|---|---|---|
| www.spiegel.de | (S+) Iran-Krieg: So läuft die doppelte Blockade der Straße von Hormus | de | 1470 | article |  | article | International Relations/Diplomacy & summits |
| newsletter-tracking.dw.com |  |  | 371 | fallback | newsletter-host |  | SKIP |
| www.bbc.com | BBC Home \| Breaking News, World News, US News, Sports, Business, Innovation, Climate, Culture, Travel, Video & Audio | en | 2000 | article |  | website | not-article |
| www.theguardian.com | US and Iran in indirect talks to extend two-week ceasefire | en | 2000 | article |  | article | International Relations/Diplomacy & summits |
| www.spiegel.de | (S+) Work-Life-Balance: Wie Sie trotz 40-Stunden-Woche Zeit für sich finden | de | 1413 | article |  | article | Society/General |
| newsletter-tracking.dw.com |  |  | 371 | fallback | newsletter-host |  | SKIP |
| www.bbc.com | BBC Home \| Breaking News, World News, US News, Sports, Business, Innovation, Climate, Culture, Travel, Video & Audio | en | 2000 | article |  | website | not-article |
| us18.campaign-archive.com | Today's most read articles on Haaretz | en | 1437 | fallback | newsletter-host |  | SKIP |
| www.theguardian.com | US extends waiver allowing countries to buy Russian oil – as it happened | en | 2000 | article |  | article | International Relations/Diplomacy & summits |
| view.angebote.spiegel.de |  |  | 136 | fallback | newsletter-host |  | SKIP |
| newsmaker.md | (ВИДЕО) Политпредставители Кишинева и Тирасполя обменялись резкими репликами во время встречи «1+1» | ru | 2000 | p-container |  | article | International Relations/Diplomacy & summits |
| www.spiegel.de | Iran öffnet Straße von Hormus, USA bleiben bei Blockade | de | 2000 | article |  | article | International Relations/Diplomacy & summits |
| www.spiegel.de | Meinung: News des Tages: Iran gibt Straße von Hormus frei, AfD überholt CDU, Waldrama vor Poel | de | 2000 | article |  | article | International Relations/Diplomacy & summits |
| newsletter-tracking.dw.com |  |  | 402 | fallback | newsletter-host |  | SKIP |
| newsletter-tracking.dw.com |  |  | 371 | fallback | newsletter-host |  | SKIP |
| www.dw.com | Война в Украине | ru | 675 | p-container |  |  | Conflicts & Security/Civil wars & insurgencies |
| www.dw.com | News and current affairs from Germany and around the world | en | 552 | p-container |  |  | not-article |
| us18.campaign-archive.com | The state of Israel: Trump says Israel ‘PROHIBITED’ from bombing Lebanon as cease-fire takes effect | en | 2000 | fallback | newsletter-host |  | SKIP |
| www.spiegel.de | Iran droht mit Schließung der Straße von Hormus – Trump mit einem Ende der Waffenruhe | de | 2000 | article |  | article | International Relations/Diplomacy & summits |
| www.spiegel.de | (S+) David gegen Goliath: Ein Krebsmediziner legt sich mit einem globalen Pharmariesen an | de | 1402 | article |  | article | Health & Medicine/Diseases & outbreaks |
| newsmaker.md | Зарплаты растут быстрее экономики. Что еще происходит с экономикой Молдовы?  | ru | 2000 | p-container |  | article | Economy & Finance/Macroeconomy & inflation |
| www.spiegel.de | Iran schließt die Straße von Hormus erneut | de | 2000 | article |  | article | International Relations/Diplomacy & summits |
| email.useinsider.com | 404 - Page Not Found |  | 351 | p-container | not-found |  | SKIP |
| www.spiegel.de | Iran, USA und die Waffenruhe: Teheran sieht weiter Differenzen mit Washington | de | 2000 | article |  | article | International Relations/Diplomacy & summits |
| www.spiegel.de | Iran, USA und die Waffenruhe: Teheran sieht weiter Differenzen mit Washington | de | 2000 | article |  | article | International Relations/Diplomacy & summits |
| us18.campaign-archive.com | Inside the Israel-Hezbollah truce in Lebanon and the gaps that could unravel it | en | 1872 | fallback | newsletter-host |  | SKIP |
| www.spiegel.de | Iran-Krieg: Donald Trump kündigt weitere Verhandlungen in Pakistan an | de | 2000 | article |  | article | International Relations/Diplomacy & summits |
| email.useinsider.com | 404 - Page Not Found |  | 351 | p-container | not-found |  | SKIP |
| newsletter-tracking.dw.com |  |  | 402 | fallback | newsletter-host |  | SKIP |
| www.spiegel.de | Irankrieg: Teheran droht mit Vergeltung für US-Beschuss von Frachtschiff | de | 2000 | article |  | article | Conflicts & Security/Interstate wars |
| www.theguardian.com | US military seizes Iran-flagged ship trying to pass strait of Hormuz blockade | en | 2000 | article |  | article | Conflicts & Security/Military & defense |
| www.spiegel.de | Irankrieg: Teheran droht mit Vergeltung für US-Beschuss von Frachtschiff | de | 2000 | article |  | article | Conflicts & Security/Interstate wars |
| www.bbc.com | BBC Home \| Breaking News, World News, US News, Sports, Business, Innovation, Climate, Culture, Travel, Video & Audio | en | 2000 | article | root-url | website | SKIP |
| www.bbc.com | BBC News - Breaking news, video and the latest top stories from the U.S. and around the world | en | 2000 | article |  | website | not-article |
| email.useinsider.com | 404 - Page Not Found |  | 351 | p-container | not-found |  | SKIP |
| newsletter-tracking.dw.com |  |  | 45 | fallback | newsletter-host |  | SKIP |
| www.theguardian.com | Virginia voters approve new congressional maps in blow to Trump | en | 2000 | article |  | article | Politics & Government/Parties & leaders |
| newsmaker.md | Еврокомиссар Марта Кос оценила прогресс Молдовы на пути в ЕС | ru | 2000 | p-container |  | article | International Relations/Diplomacy & summits |
| mailchi.mp | NM Espresso 22 апреля: чем в Молдове заменят ЧП, что будет с налогом на недвижимость, и ждут ли Румынию новые выборы | ru | 2000 | p-container | newsletter-host |  | SKIP |
| newsmaker.md | В Молдове ЧП в энергетике могут заменить на режим повышенной готовности | ru | 2000 | p-container |  | article | Politics & Government/Government & legislation |
| www.theguardian.com | Kim Jong-un offers Putin ‘full support’ in Russia’s ‘sacred fight’ with west | en | 2000 | article |  | article | International Relations/Diplomacy & summits |
| www.theguardian.com | ‘Impossible’ to reopen strait of Hormuz amid ‘flagrant’ ceasefire breaches, Iran says | en | 2000 | article |  | article | Conflicts & Security/Civil wars & insurgencies |
| www.spiegel.de | (S+) Irankrieg als Scheidungsgrund: Tucker Carlson rechnet mit Donald Trump ab | de | 1386 | article |  | article | Politics & Government/Parties & leaders |
| www.spiegel.de | (S+) Schwedt-Raffinerie: Das wären die Folgen einer russischen Ölblockade | de | 1361 | article |  | article | Economy & Finance/General |
| www.theguardian.com | Trump claims US has total control over strait of Hormuz after Iran seizes two container ships | en | 2000 | article |  | article | International Relations/Diplomacy & summits |
| www.spiegel.de | (S+) Marine-Staatssekretär John Phlean: Donald Trump schwärmte von ihm – dennoch muss er nun gehen | de | 1408 | article |  | article | Politics & Government/Government & legislation |
| newsmaker.md | Глава полиции о требовании оппозиции уйти в отставку: «Подчинюсь, если решение примет мое руководство» | ru | 2000 | p-container |  | article | International Relations/Diplomacy & summits |
| newsmaker.md | Драгалин поздравила бывших коллег из прокуратуры после приговора Плахотнюку: «Я знаю изнутри, сколько труда за этим стоит» | ru | 2000 | p-container |  | article | International Relations/Diplomacy & summits |
| newsmaker.md | ЧП в цифрах. Что происходит в энергетике Молдовы после кризиса? | ru | 2000 | p-container |  | article | Energy/Energy prices & supply routes |
| email.useinsider.com | 404 - Page Not Found |  | 351 | p-container | not-found |  | SKIP |
| email.useinsider.com | 404 - Page Not Found |  | 351 | p-container | not-found |  | SKIP |
| www.spiegel.de | (S+) Haushalt 2027: Abgaben auf Zucker, Plastik, Tabak – so will die Regierung Milliarden reinholen | de | 1403 | article |  | article | Economy & Finance/Public finance & taxes |
| www.spiegel.de | (S+) Warum die Rede von König Charles im US-Kongress ein diplomatisches Meisterstück war - die Analyse | de | 1482 | article |  | article | International Relations/Diplomacy & summits |
| www.theguardian.com | Trump calls Iran’s response to peace plan ‘totally unacceptable’ as ceasefire frays | en | 2000 | article |  | article | International Relations/Diplomacy & summits |
| www.theguardian.com | Democrats express ‘grave concerns’ over secretive ICE deportation flights | en | 2000 | article |  | article | Politics & Government/Parties & leaders |
| www.theguardian.com | Trump says ceasefire with Iran on ‘life support’ after rejecting peace proposals | en | 2000 | article |  | article | International Relations/Peace talks & ceasefires |
| newsmaker.md | Фуртунэ через суд требует признать «захват власти правящей партией». Реакция PAS | ru | 2000 | p-container |  | article | International Relations/Diplomacy & summits |
| newsmaker.md | Гросу не планирует уходить с поста председателя PAS: Я баллотируюсь на новый срок | ru | 2000 | p-container |  | article | Politics & Government/Parties & leaders |
| www.theguardian.com | Vostochny cosmodrome: the remote Russian spaceport hosting Kim and Putin | en | 2000 | article |  | article | International Relations/Diplomacy & summits |
| www.bbc.com | US politics - BBC News | en | 1211 | article |  |  | Politics & Government/Elections |
| www.spiegel.de | (S+) US-Präsident in China: Symbolik schlägt Substanz – fünf Gipfelerkenntnisse | de | 1341 | article |  | article | International Relations/Diplomacy & summits |
| view.angebote.spiegel.de |  |  | 137 | fallback | newsletter-host |  | SKIP |
