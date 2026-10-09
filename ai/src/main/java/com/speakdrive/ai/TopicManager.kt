package com.speakdrive.ai

import com.speakdrive.ai.model.Scenario
import com.speakdrive.ai.model.Topic
import com.speakdrive.ai.model.TopicCategory
import java.text.Normalizer
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Single source of truth for conversation topics, used by the phone UI,
 * the Android Auto media tree and voice search.
 */
@Singleton
class TopicManager @Inject constructor() {

    private val topics = listOf(
        Topic(
            id = "travel",
            category = TopicCategory.TRAVEL,
            titleVi = "Du lịch & Đi lại",
            titleEn = "Travel & Getting Around",
            description = "Travel plans, trips, airports, hotels, emergencies and asking for directions.",
            emoji = "🏖️",
            keywords = listOf(
                "travel", "trip", "holiday", "vacation", "airport", "hotel", "flight", "plane", "customs",
                "immigration", "boarding", "luggage", "baggage", "resort", "hostel", "cruise", "tour",
                "sightseeing", "visa", "ticket", "du lich", "di lai", "ve may bay", "san bay", "khach san",
                "hai quan", "hanh ly", "dat phong", "huong dan vien", "lac duong", "tour du lich", "mat ho chieu", "tre chuyen bay"
            ),
            scenarios = listOf(
                Scenario("travel_hotel", "Nhận phòng khách sạn", "Checking into a hotel", "a friendly hotel receptionist", "a guest checking in", missionObjective = "Confirm your reservation, ask about breakfast hours and request a quiet room on a high floor."),
                Scenario("travel_airport", "Làm thủ tục ở sân bay", "At the airport check-in", "an airline check-in agent", "a passenger flying abroad", missionObjective = "Check in your bags, confirm transit regulations and request an aisle seat."),
                Scenario("travel_directions", "Hỏi đường", "Asking for directions", "a helpful local on the street", "a tourist who is lost", missionObjective = "Find the nearest subway station and ask how many stops until downtown."),
                Scenario("travel_taxi", "Đi taxi", "Taking a taxi", "a chatty taxi driver", "a passenger going downtown", missionObjective = "Give clear destination instructions, ask about traffic conditions and confirm payment method."),
                Scenario(
                    "travel_lost_passport",
                    "Mất hộ chiếu & Khẩn cấp Đại sứ quán",
                    "Lost passport & Embassy emergency",
                    "an embassy consular officer handling emergency requests",
                    "a stressed traveler who lost their passport in a foreign city",
                    "The traveler's backpack containing their passport, wallet and flight ticket was stolen on the metro.",
                    "Explain your emergency situation clearly, provide identification details, and request an emergency travel certificate."
                ),
                Scenario(
                    "travel_missed_flight",
                    "Xử lý trễ chuyến bay nối chuyến",
                    "Missed connecting flight & Rebooking",
                    "a busy airline customer service agent at the transit desk",
                    "a stranded passenger whose incoming flight was delayed by weather",
                    "The passenger arrived at the terminal 15 minutes after the only daily flight to London departed.",
                    "Negotiate calmly to get rebooked on the earliest partner flight and request complimentary hotel and meal vouchers."
                ),
                Scenario(
                    "travel_hotel_issue",
                    "Khiếu nại máy lạnh hỏng & Đổi phòng",
                    "Broken AC & Requesting a room upgrade",
                    "a front desk duty manager at midnight",
                    "a tired hotel guest whose room is 32°C and AC is rattling loudly",
                    "The hotel is near full capacity. The room AC stopped cooling and emits a grinding noise.",
                    "Explain the unacceptable condition firmly and convince the manager to upgrade you to an executive room without extra fees."
                ),
                Scenario(
                    "travel_car_rental_police",
                    "Thuê xe & Cảnh sát giao thông kiểm tra",
                    "Rental car & Highway traffic check",
                    "a strict highway patrol trooper on interstate highway",
                    "an international traveler driving a rental SUV",
                    "You were pulled over for driving slightly below minimum speed in the passing lane while looking for an exit sign.",
                    "Remain polite, explain your unfamiliarity with local road numbers, present your international permit, and resolve with a warning."
                )
            )
        ),
        Topic(
            id = "work",
            category = TopicCategory.WORK,
            titleVi = "Công việc & Kinh doanh",
            titleEn = "Work & Business",
            description = "Jobs, meetings, colleagues, projects, performance reviews and business negotiations.",
            emoji = "💼",
            keywords = listOf(
                "work", "business", "job", "office", "meeting", "colleague", "presentation", "pitch",
                "quarterly", "strategy", "contract", "negotiation", "sales", "marketing", "finance",
                "revenue", "deadline", "cong viec", "kinh doanh", "van phong", "hop", "thuyet trinh",
                "hop dong", "dam phan", "chien luoc", "dong nghiep", "tang luong", "khach hang"
            ),
            scenarios = listOf(
                Scenario("work_meeting", "Họp nhóm", "Team meeting", "a team manager running a weekly meeting", "a team member giving an update", missionObjective = "Summarize your weekly deliverables, report a minor blocker, and propose a solution."),
                Scenario("work_client", "Gặp khách hàng", "Meeting a client", "a potential client", "a salesperson presenting a product", missionObjective = "Highlight three core advantages of your solution and address their pricing concerns."),
                Scenario("work_boss", "Xin nghỉ phép", "Asking your boss for leave", "a busy but fair boss", "an employee asking for time off", missionObjective = "Explain the personal reason for 3 days off and demonstrate how your tasks will be covered."),
                Scenario("work_smalltalk", "Trò chuyện với đồng nghiệp", "Small talk with a colleague", "a friendly coworker at the coffee machine", "a colleague", missionObjective = "Share light weekend updates and chat about the upcoming company team-building event."),
                Scenario(
                    "work_salary_negotiation",
                    "Đàm phán tăng lương định kỳ",
                    "Annual performance & Salary review",
                    "a results-driven department director",
                    "a high-performing employee requesting a 20% salary increase",
                    "Annual review meeting. You exceeded revenue targets by 30% and took over leading junior members.",
                    "Articulate your key business contributions with metrics and convince your boss to approve the 20% compensation increase."
                ),
                Scenario(
                    "work_difficult_client",
                    "Xử lý khách hàng VIP đòi hủy hợp đồng",
                    "De-escalating an angry VIP client",
                    "a furious corporate client whose operations were disrupted by downtime",
                    "a senior account manager managing the crisis",
                    "The client lost critical orders during a platform outage and is threatening legal cancellation.",
                    "Acknowledge the business impact with deep empathy, outline the technical fix, and propose service credits to save the contract."
                ),
                Scenario(
                    "work_pitch_investor",
                    "Pitching gọi vốn trước nhà đầu tư",
                    "Startup elevator pitch to an Angel investor",
                    "a sharp, skeptical venture capitalist",
                    "a passionate startup founder pitching an AI voice assistant",
                    "You have 3 minutes in an executive lounge to pitch your early-stage venture.",
                    "Deliver a punchy problem-solution statement, explain market traction, and secure a follow-up formal pitch meeting."
                ),
                Scenario(
                    "work_remote_request",
                    "Thuyết phục sếp cho làm việc Remote Hybrid",
                    "Proposing a flexible remote-work schedule",
                    "a traditional manager who values in-office presence",
                    "an employee proposing a 2-day work-from-home schedule",
                    "Your daily commute takes 2 hours. You want to work from home on Tuesdays and Thursdays.",
                    "Propose clear productivity metrics, reliable availability hours, and convince your boss to agree to a 3-month trial."
                )
            )
        ),
        Topic(
            id = "food",
            titleVi = "Ăn uống & Nhà hàng",
            titleEn = "Food & Dining",
            description = "Food, cooking, fine dining, dietary preferences, and resolving restaurant issues.",
            emoji = "🍽️",
            keywords = listOf(
                "food", "restaurant", "dining", "eat", "cooking", "meal", "menu", "dish", "beverage",
                "coffee", "snack", "dessert", "taste", "flavor", "recipe", "chef", "an uong", "nha hang",
                "mon an", "thuc don", "ca phe", "nau an", "goi mon", "di ung", "tinh tien"
            ),
            scenarios = listOf(
                Scenario("food_order", "Gọi món ở nhà hàng", "Ordering at a restaurant", "a waiter at a busy restaurant", "a customer ordering dinner", missionObjective = "Order a three-course dinner, ask for recommendations and specify meat doneness."),
                Scenario("food_coffee", "Mua cà phê", "Ordering coffee", "a barista at a coffee shop", "a customer", missionObjective = "Order a customized espresso drink with specific milk and sugar preferences."),
                Scenario("food_complaint", "Phàn nàn về món ăn", "Complaining about a dish", "a restaurant manager", "a customer whose dish is wrong", missionObjective = "Explain politely that the steak was overcooked and request a replacement."),
                Scenario("food_recipe", "Chia sẻ công thức", "Sharing a recipe", "a curious friend who loves cooking", "someone explaining a Vietnamese dish", missionObjective = "Explain the essential ingredients and steps to cook authentic Vietnamese Pho or Spring Rolls."),
                Scenario(
                    "food_allergy",
                    "Dị ứng thực phẩm nghiêm trọng",
                    "Severe food allergy consultation",
                    "a very attentive head waiter",
                    "a diner with severe peanut and shellfish allergies",
                    "Cross-contamination could trigger severe anaphylaxis. You are dining at an unfamiliar seafood restaurant.",
                    "Communicate your allergies with utmost clarity and verify that the chef uses separate cookware for your meal."
                ),
                Scenario(
                    "food_bill_dispute",
                    "Tranh chấp hóa đơn tính nhầm tiền",
                    "Disputing an incorrect restaurant bill",
                    "a restaurant shift supervisor",
                    "a customer whose bill includes bottles of vintage wine they never ordered",
                    "The bill total is $180 higher than expected due to another table's drinks mistakenly charged.",
                    "Politely but firmly point out each incorrect line item and ensure the bill is recalculated accurately."
                ),
                Scenario(
                    "food_surprise_party",
                    "Đặt bàn & Tổ chức tiệc sinh nhật bất ngờ",
                    "Arranging a surprise birthday dinner",
                    "a restaurant event coordinator",
                    "a customer planning a surprise celebration for 8 people",
                    "You want sparklers with the dessert, a specific quiet window table, and secret cake storage.",
                    "Coordinate the exact schedule, secret hand signals with waiters, and dietary options for all guests."
                )
            )
        ),
        Topic(
            id = "shopping",
            titleVi = "Mua sắm",
            titleEn = "Shopping",
            description = "Buying things, bargaining, electronics, returns, tax refunds and online shopping.",
            emoji = "🛒",
            keywords = listOf(
                "shopping", "shop", "buy", "store", "sale", "discount", "deal", "price", "cost",
                "receipt", "refund", "exchange", "cashier", "market", "mall", "mua sam", "mua hang",
                "giam gia", "khuyen mai", "doi tra", "tra tien", "sieu thi", "cho", "mac ca", "hoan thue"
            ),
            scenarios = listOf(
                Scenario("shopping_clothes", "Mua quần áo", "Buying clothes", "a shop assistant in a clothing store", "a customer looking for a jacket", missionObjective = "Find your size, ask to try it on in the fitting room, and inquire about discounts."),
                Scenario("shopping_return", "Đổi trả hàng", "Returning an item", "a customer service clerk", "a customer returning a broken item", missionObjective = "Present the receipt, explain the product defect, and obtain a full refund or exchange."),
                Scenario("shopping_market", "Đi chợ", "At the market", "a market vendor", "a customer bargaining for fruit", missionObjective = "Negotiate a fair price for 2 kilograms of fresh mangoes and strawberries."),
                Scenario("shopping_phone", "Mua điện thoại", "Buying a phone", "an electronics store salesperson", "a customer comparing phones", missionObjective = "Compare battery life and camera specs between two flagship models and choose one."),
                Scenario(
                    "shopping_bargain_flea",
                    "Mặc cả tại hội chợ đồ cổ",
                    "Bargaining at a vintage flea market",
                    "a stubborn antique stall dealer",
                    "a savvy traveler negotiating for a vintage leather jacket and camera",
                    "The dealer asks $140. The jacket zipper is slightly stiff and the camera lens needs cleaning.",
                    "Bargain the price down to $80 by highlighting minor flaws while keeping the conversation light and humorous."
                ),
                Scenario(
                    "shopping_damaged_delivery",
                    "Khiếu nại kiện hàng trực tuyến bị vỡ",
                    "Complaining about damaged online delivery",
                    "an e-commerce support specialist",
                    "a customer whose expensive ceramic espresso machine arrived shattered",
                    "The package arrived crushed with glass fragments inside. You bought it as a wedding gift for tomorrow.",
                    "Provide your order ID, describe the package damage clearly, and request expedited replacement shipment."
                ),
                Scenario(
                    "shopping_tax_refund",
                    "Làm thủ tục hoàn thuế sân bay",
                    "Airport customs tax refund",
                    "a busy customs officer at an international airport",
                    "a traveler claiming VAT tax refund with receipts",
                    "Your boarding begins in 35 minutes and the tax refund line is bustling.",
                    "Present invoices, export stamps and unopened merchandise quickly to secure immediate card refund."
                )
            )
        ),
        Topic(
            id = "health",
            category = TopicCategory.TRAVEL,
            titleVi = "Y tế & Sức khỏe",
            titleEn = "Health & Medical",
            description = "Health, fitness, ER visits, symptoms, pharmacies, and physical wellness.",
            emoji = "🏥",
            keywords = listOf(
                "health", "medical", "doctor", "hospital", "fitness", "wellness", "exercise", "workout",
                "gym", "cold", "fever", "headache", "flu", "pharmacy", "medicine", "clinic", "appointment",
                "y te", "suc khoe", "kham benh", "cam cum", "phong gym", "hieu thuoc", "nha thuoc", "cap cuu", "nha khoa"
            ),
            scenarios = listOf(
                Scenario("health_doctor", "Khám bệnh", "At the doctor's", "a kind family doctor", "a patient with a sore throat", missionObjective = "Describe your symptoms, how long you've had fever, and ask about side effects of medication."),
                Scenario("health_pharmacy", "Mua thuốc", "At the pharmacy", "a pharmacist", "a customer asking for medicine", missionObjective = "Ask for over-the-counter pain relievers and clarify dosage instructions."),
                Scenario("health_gym", "Đăng ký phòng gym", "Joining a gym", "a gym receptionist", "a new member", missionObjective = "Compare monthly vs annual plans and ask about personal trainer sessions."),
                Scenario("health_appointment", "Đặt lịch khám", "Booking an appointment", "a clinic receptionist on the phone", "a patient booking a visit", missionObjective = "Find an available doctor slot on Thursday afternoon and verify health insurance coverage."),
                Scenario(
                    "health_er_emergency",
                    "Cấp cứu khẩn cấp: Ngộ độc thức ăn",
                    "Emergency Room: Acute symptom triage",
                    "an urgent care triage nurse",
                    "a patient experiencing acute abdominal cramps and dehydration",
                    "Severe food poisoning symptoms started 2 hours after street seafood dinner: vomiting, dizziness and high fever.",
                    "Detail your symptom onset, rate pain on 1 to 10 scale, list medical allergies, and get rapid medical attention."
                ),
                Scenario(
                    "health_dentist",
                    "Khám nha khoa: Đau buốt răng sâu",
                    "Emergency dental visit for toothache",
                    "a gentle dental specialist",
                    "a patient suffering sharp throbbing tooth pain when drinking liquids",
                    "Severe throbbing molar pain kept you awake all night. You fear needing an extraction.",
                    "Describe the sensation precisely, ask about tooth filling vs root canal treatment, and discuss local anesthesia."
                ),
                Scenario(
                    "health_personal_trainer",
                    "Tư vấn huấn luyện viên thể hình",
                    "Consulting a personal trainer after injury",
                    "an experienced strength coach",
                    "a gym member recovering from a knee sprain looking for safe exercises",
                    "Doctor cleared you for low-impact training. You want to rebuild leg strength without risking cartilage strain.",
                    "Communicate physical limitations, define a 3-month fitness goal, and agree on a customized workout routine."
                )
            )
        ),
        Topic(
            id = "entertainment",
            titleVi = "Giải trí & Phim ảnh",
            titleEn = "Entertainment & Movies",
            description = "Movies, music, hobbies, gaming, streaming, concerts and cultural arts.",
            emoji = "🎭",
            keywords = listOf(
                "entertainment", "movie", "movies", "film", "music", "hobby", "cinema", "concert",
                "show", "theater", "game", "gaming", "novel", "book", "stream", "giai tri", "phim",
                "dien anh", "ca nhac", "hoa nhac", "tro choi", "rap chieu phim", "so thich", "dien tu"
            ),
            scenarios = listOf(
                Scenario("ent_movie", "Bàn về một bộ phim", "Talking about a movie", "a friend who just saw the same movie", "a movie fan", missionObjective = "Discuss plot twists, critique the acting performance, and recommend another director's work."),
                Scenario("ent_tickets", "Mua vé xem phim", "Buying cinema tickets", "a cinema ticket seller", "a customer", missionObjective = "Choose preferred IMAX 3D seats and order popcorn and drink combos."),
                Scenario("ent_concert", "Rủ bạn đi xem hòa nhạc", "Inviting a friend to a concert", "a friend with a busy schedule", "someone inviting them", missionObjective = "Persuade your friend to attend a live weekend music festival by highlighting their favorite bands."),
                Scenario("ent_hobby", "Giới thiệu sở thích", "Sharing your hobbies", "a new acquaintance at a party", "someone talking about hobbies", missionObjective = "Describe your passion for photography or guitar playing and explain how you got started."),
                Scenario(
                    "ent_game_stream",
                    "Trò chuyện về game & Livestream",
                    "Discussing gaming & esports",
                    "an avid esports fan and streamer",
                    "a gamer discussing gameplay meta and tournament strategies",
                    "Reviewing a world championship tournament finals between two rival teams.",
                    "Break down the winning tactics, share your favorite game characters, and debate next season's balance updates."
                ),
                Scenario(
                    "ent_book_club",
                    "Họp câu lạc bộ đọc sách",
                    "Book club discussion on mystery novels",
                    "an enthusiastic book club moderator",
                    "a reader sharing thoughts on a psychological thriller",
                    "Monthly book club meeting analyzing a thrilling whodunit novel.",
                    "Share your perspective on the unreliable narrator, examine moral dilemmas in the book, and rate the ending."
                )
            )
        ),
        Topic(
            id = "daily",
            titleVi = "Giao tiếp hàng ngày",
            titleEn = "Daily Conversation",
            description = "Everyday life: networking, cultural exchanges, neighbor disputes, and setting personal boundaries.",
            emoji = "💬",
            keywords = listOf(
                "daily", "everyday", "small talk", "life", "routine", "weather", "forecast", "neighbor",
                "weekend", "family", "friend", "chat", "giao tiep", "hang ngay", "thoi tiet", "hang xom",
                "cuoi tuan", "ban be", "doi song", "networking", "tet", "van hoa"
            ),
            scenarios = listOf(
                Scenario("daily_neighbor", "Chào hỏi hàng xóm", "Chatting with a neighbour", "a friendly neighbour", "a neighbour", missionObjective = "Exchange pleasantries about the garden, comment on weekend weather, and say goodbye."),
                Scenario("daily_weekend", "Kế hoạch cuối tuần", "Weekend plans", "a close friend", "a friend making plans", missionObjective = "Coordinate a Sunday morning bicycle ride and agree on a meeting spot."),
                Scenario("daily_phone", "Gọi điện cho bạn", "Calling a friend", "an old friend on the phone", "the caller", missionObjective = "Catch up on recent life events and congratulate them on their new home."),
                Scenario("daily_new_friend", "Làm quen bạn mới", "Making a new friend", "a friendly stranger at a cafe", "someone starting a conversation", missionObjective = "Break the ice naturally, discover shared interests, and exchange contact info."),
                Scenario(
                    "daily_networking",
                    "Bắt chuyện tại tiệc Networking quốc tế",
                    "Networking at an international mixer",
                    "a foreign entrepreneur working in Southeast Asia",
                    "a professional attending an international business mixer",
                    "Standing by the refreshments table after a keynote address on tech innovation.",
                    "Break the ice smoothly, ask about their business ventures, share your background, and exchange LinkedIn connections."
                ),
                Scenario(
                    "daily_vietnamese_culture",
                    "Giới thiệu văn hóa Tết & Ẩm thực Việt",
                    "Explaining Vietnamese Tet & Street food",
                    "a curious foreign coworker visiting Vietnam during spring",
                    "a local sharing cultural traditions, family reunions, and food",
                    "Your foreign friend is amazed by bustling flower markets and asks why everyone buys kumquat trees.",
                    "Explain the spiritual meaning of Tet holiday, family banquets, and recommend must-try local specialties."
                ),
                Scenario(
                    "daily_noise_complaint",
                    "Hòa giải mâu thuẫn tiếng ồn với hàng xóm",
                    "Politely resolving a neighbor noise dispute",
                    "a neighbor who frequently plays loud music late on weeknights",
                    "an apartment resident who needs to wake up early for work",
                    "It is 11:30 PM on a Tuesday. Loud stereo bass has been vibrating through your shared wall for 2 hours.",
                    "Address the noise issue diplomatically without hostility, reach a mutual understanding, and agree on quiet hours."
                ),
                Scenario(
                    "daily_decline_loan",
                    "Từ chối khéo lời mời vay tiền",
                    "Gracefully declining an awkward financial request",
                    "an old acquaintance who calls asking for a large emergency loan",
                    "a sensible friend setting healthy personal boundaries",
                    "An old acquaintance you haven't spoken with in years suddenly requests a $1,000 personal loan.",
                    "Express empathy for their hardship while maintaining firm, polite financial boundaries without damaging goodwill."
                )
            )
        ),
        Topic(
            id = "interview",
            category = TopicCategory.WORK,
            titleVi = "Phỏng vấn xin việc",
            titleEn = "Job Interview",
            description = "Job interviews: introductions, behavioral questions, failures, salary and equity negotiations.",
            emoji = "🎯",
            keywords = listOf(
                "interview", "job interview", "career", "resume", "cv", "candidate", "recruiter", "hr",
                "experience", "skills", "qualification", "salary", "offer", "phong van", "xin viec",
                "ho so", "ung vien", "nha tuyen dung", "kinh nghiem", "ky nang", "luong", "co phieu"
            ),
            scenarios = listOf(
                Scenario("interview_intro", "Giới thiệu bản thân", "Tell me about yourself", "an HR interviewer", "a job candidate", missionObjective = "Give a crisp 2-minute elevator pitch summarizing your career arc, key wins and why this role fits."),
                Scenario("interview_behavioral", "Câu hỏi tình huống", "Behavioural questions", "a hiring manager", "a candidate", missionObjective = "Answer using the STAR method (Situation, Task, Action, Result) to describe resolving a tight deadline crisis."),
                Scenario("interview_strengths", "Điểm mạnh & điểm yếu", "Strengths and weaknesses", "a senior interviewer", "a candidate", missionObjective = "Discuss two genuine professional strengths and one real growth area with concrete steps you're taking."),
                Scenario("interview_salary", "Đàm phán lương", "Salary negotiation", "an HR manager making an offer", "a candidate negotiating", missionObjective = "Negotiate a 15% increase above their initial offer by citing market benchmarks and proven track record."),
                Scenario(
                    "interview_greatest_failure",
                    "Bài học từ thất bại lớn nhất",
                    "Describe your greatest career setback",
                    "a discerning executive interviewer evaluating humility and resilience",
                    "a candidate interviewing for a leadership position",
                    "The interviewer wants to see how you handle failure, accountability, and psychological resilience.",
                    "Describe a genuine project setback objectively, take ownership of mistakes, and articulate the lasting lessons learned."
                ),
                Scenario(
                    "interview_stock_options",
                    "Thương lượng quyền chọn cổ phiếu & Phúc lợi",
                    "Negotiating startup equity & benefits",
                    "a startup talent director presenting an offer package",
                    "a senior candidate evaluating base salary vs equity options",
                    "The base salary offer is 10% below market, but the company offers stock options with high growth potential.",
                    "Negotiate for additional stock options, an accelerated vesting schedule, or signing bonus to bridge the gap."
                ),
                Scenario(
                    "interview_conflict_resolution",
                    "Xử lý xung đột bất đồng trong nhóm",
                    "Resolving team disputes & cross-functional friction",
                    "a VP evaluating your emotional intelligence and collaboration",
                    "a candidate explaining a real interpersonal conflict",
                    "Explain how you resolved a major deadlock between engineering priorities and marketing launch deadlines.",
                    "Demonstrate empathy, de-escalation tactics, and show how you led the team toward a shared, data-driven compromise."
                )
            )
        ),
        Topic(
            id = "driving_emergency",
            category = TopicCategory.TRAVEL,
            titleVi = "Lái xe & Sự cố Giao thông",
            titleEn = "Driving & Roadside Situations",
            description = "Hands-free road assistance, flat tires, traffic stops, navigation detours and ridesharing.",
            emoji = "🚗",
            keywords = listOf(
                "driving", "roadside", "flat tire", "tow truck", "police", "traffic", "highway", "interstate",
                "gas station", "gps", "rideshare", "detour", "mechanic", "breakdown", "lai xe", "su co", "giao thong",
                "xi lop", "cuu ho", "canh sat", "bat xe", "cao toc", "lac duong"
            ),
            scenarios = listOf(
                Scenario(
                    "drive_flat_tire",
                    "Xì lốp xe gọi cứu hộ khẩn cấp ven cao tốc",
                    "Highway roadside assistance for flat tire",
                    "a 24/7 roadside emergency dispatcher",
                    "a driver stranded on the shoulder of Interstate 95",
                    "Rear right tire blew out at 65 mph. You safely pulled over to the narrow highway shoulder as dusk is falling.",
                    "State your exact highway mile marker, vehicle make and model, confirm safety position, and request an emergency tow truck."
                ),
                Scenario(
                    "drive_traffic_police",
                    "Cảnh sát giao thông kiểm tra bằng lái",
                    "Routine traffic stop & license check",
                    "a professional highway patrol trooper",
                    "a foreign driver pulled over for driving in the carpool HOV lane",
                    "You didn't realize the diamond HOV lane required two or more passengers during morning peak hours.",
                    "Keep hands visible on the steering wheel, present license and rental papers calmly, explain the honest mistake, and ask for a warning."
                ),
                Scenario(
                    "drive_lost_gps",
                    "Lạc đường mất sóng GPS hỏi người đi bộ",
                    "Lost in rural area without cellular GPS",
                    "a friendly rural gas station owner",
                    "a lost driver trying to find the scenic coastal highway",
                    "Phone battery is at 3% and there is 'No Service' across the mountain pass.",
                    "Ask for clear landmark-based driving directions, turns, and how far until the nearest town."
                ),
                Scenario(
                    "drive_rideshare_rider",
                    "Đón khách xe công nghệ & Đổi lộ trình",
                    "Rideshare driving: Navigating passenger requests",
                    "a hurried airport passenger asking to take an unlisted detour",
                    "a courteous rideshare driver navigating city traffic",
                    "The passenger wants to make a quick 5-minute stop at a dry cleaner without updating the app.",
                    "Politely explain the app routing policy, accommodate their request safely, and maintain a 5-star rating."
                )
            )
        ),
        Topic(
            id = "social_dating",
            titleVi = "Hẹn hò, Kết bạn & Xã hội",
            titleEn = "Socializing & Dating",
            description = "First dates, community clubs, helping travelers, and deepening friendships.",
            emoji = "☕",
            keywords = listOf(
                "social", "dating", "date", "friendship", "party", "club", "meetup", "running club",
                "farewell", "colleague", "hen ho", "ket ban", "xa hoi", "ban be", "chia tay", "chay bo"
            ),
            scenarios = listOf(
                Scenario(
                    "social_first_date",
                    "Buổi hẹn hò đầu tiên tại quán cà phê",
                    "First coffee date conversation",
                    "an engaging date partner with diverse creative hobbies",
                    "someone on a casual first date getting to know each other",
                    "You both love specialty coffee, traveling, and indie cinema. You are looking for authentic connection.",
                    "Ask thoughtful open-ended questions, share genuine stories with humor, and gauge mutual chemistry."
                ),
                Scenario(
                    "social_running_club",
                    "Tham gia câu lạc bộ chạy bộ cuối tuần",
                    "Joining a weekend community running club",
                    "a welcoming running group captain",
                    "a newcomer joining a 10K morning run",
                    "You want to train for a half-marathon and meet energetic fitness enthusiasts.",
                    "Introduce yourself, discuss pace groups, ask about training schedules, and join the post-run coffee."
                ),
                Scenario(
                    "social_foreign_tourist",
                    "Giúp đỡ du khách nước ngoài tham quan",
                    "Helping a foreign tourist navigate Hanoi/Saigon",
                    "an overwhelmed backpacker looking for authentic local sights",
                    "a helpful local resident",
                    "The backpacker is trying to avoid tourist traps and wants to experience real local life.",
                    "Recommend hidden gem cafes, explain how to cross the street safely, and share tips on bargaining respectfully."
                ),
                Scenario(
                    "social_farewell_colleague",
                    "Chia tay đồng nghiệp thân thiết chuyển công tác",
                    "Farewell dinner for a departing colleague",
                    "a close coworker relocating overseas for an internal transfer",
                    "a colleague reminiscing about shared projects",
                    "You've worked together on stressful launches for 3 years. Tonight is their farewell gathering.",
                    "Express heartfelt gratitude for their support, recall a funny milestone memory, and promise to keep in touch."
                )
            )
        ),
        Topic(
            id = "startup_tech",
            category = TopicCategory.WORK,
            titleVi = "Khởi nghiệp & Kỷ nguyên AI",
            titleEn = "Startups & Modern AI Era",
            description = "AI product demos, tech leadership, AI ethics debates and social media PR crisis handling.",
            emoji = "💡",
            keywords = listOf(
                "startup", "ai", "artificial intelligence", "tech", "technology", "llm", "mvp", "demo",
                "ethics", "pr", "crisis", "fundraising", "khoi nghiep", "cong nghe", "tri tue nhan tao", "khung hoang"
            ),
            scenarios = listOf(
                Scenario(
                    "tech_ai_ethics",
                    "Tranh luận đạo đức về ứng dụng AI",
                    "Debating ethics in generative AI & automation",
                    "a technology ethics researcher",
                    "a product manager designing an autonomous AI agent system",
                    "Discussing copyright, algorithmic bias, and human displacement in modern AI systems.",
                    "Articulate both the economic advantages of AI automation and the crucial ethical safeguards needed."
                ),
                Scenario(
                    "tech_mvp_demo",
                    "Demo sản phẩm thử nghiệm cho khách hàng đầu tiên",
                    "MVP product demo to beta testers",
                    "a skeptical early adopter trying out a new productivity tool",
                    "a startup founder walking them through the core workflow",
                    "The product has rough edges but solves a huge pain point in voice note transcription.",
                    "Guide the user through the 'aha!' moment, gather honest constructive critique, and secure their commitment to beta test."
                ),
                Scenario(
                    "tech_lead_interview",
                    "Phỏng vấn Tech Lead về kiến trúc AI",
                    "Technical leadership interview on AI infrastructure",
                    "a VP of Engineering testing system design and team leadership",
                    "a candidate proposing distributed LLM serving architecture",
                    "Design a low-latency speech-to-speech AI pipeline serving 100,000 concurrent drivers.",
                    "Discuss latency tradeoffs, edge vs cloud caching, fallback resilience, and team mentorship."
                ),
                Scenario(
                    "tech_pr_crisis",
                    "Xử lý khủng hoảng truyền thông mạng xã hội",
                    "Managing a social media PR crisis",
                    "a demanding CEO during an emergency executive briefing",
                    "a communications director proposing an apology and fix strategy",
                    "A buggy software update leaked anonymized user stats on Twitter, sparking online outrage.",
                    "Present a transparent crisis communications statement, explain the technical patch, and outline customer restitution."
                )
            )
        ),
        Topic(
            id = "medical_expert",
            category = TopicCategory.WORK,
            titleVi = "Y tế Chuyên sâu",
            titleEn = "Advanced Medical & Clinical Practice",
            description = "Clinical discussions, case consultations, surgical briefings, diagnosis and communicating complex medical plans for physicians.",
            emoji = "🩺",
            keywords = listOf(
                "medical", "medicine", "doctor", "physician", "surgeon", "clinical", "hospital", "surgery",
                "patient", "diagnosis", "treatment", "pathology", "pharmacology", "prescription", "consultation",
                "icu", "ward", "anesthesia", "pre op", "trial", "bac si", "y te", "y khoa", "y te chuyen sau",
                "lam sang", "hoi chan", "phau thuat", "kham benh", "benh an", "tien phau", "gay me", "noi soi",
                "duoc ly", "khang sinh", "xet nghiem", "chuyen khoa"
            ),
            scenarios = listOf(
                Scenario(
                    "med_case_presentation",
                    "Hội chẩn ca bệnh lâm sàng",
                    "Clinical case presentation",
                    "a senior consulting physician at a clinical case conference",
                    "the attending physician presenting a complex patient case",
                    missionObjective = "Present patient vitals, lab biomarkers, differential diagnoses, and recommend an intervention plan."
                ),
                Scenario(
                    "med_patient_explanation",
                    "Giải thích chẩn đoán & phác đồ",
                    "Explaining diagnosis & treatment",
                    "an anxious patient or family member asking about risks and prognosis",
                    "a compassionate doctor explaining diagnosis and treatment options",
                    missionObjective = "Explain complex medical terms in accessible language, outline potential risks, and reassure the family."
                ),
                Scenario(
                    "med_surgical_briefing",
                    "Bàn giao kíp mổ & Tiền phẫu",
                    "Pre-op surgical team briefing",
                    "the chief anesthesiologist reviewing patient vitals and risks",
                    "the lead surgeon briefing the operating room team",
                    missionObjective = "Verify patient identity, review surgical steps, anticipate blood loss, and confirm instrument sterility."
                ),
                Scenario(
                    "med_pharma_trials",
                    "Thảo luận thử nghiệm & Thuốc mới",
                    "Clinical trials & drug therapy",
                    "a clinical research specialist discussing new trial evidence",
                    "a physician evaluating clinical efficacy, interactions and side effects",
                    missionObjective = "Evaluate phase-3 trial endpoints, adverse effect profiles, and contrast with standard-of-care drugs."
                )
            )
        ),
        Topic(
            id = "tech_it",
            category = TopicCategory.WORK,
            titleVi = "Công nghệ Thông tin & Phần mềm",
            titleEn = "IT & Software Engineering",
            description = "System architecture, code reviews, cloud infrastructure, incident postmortems and sprint planning.",
            emoji = "💻",
            keywords = listOf(
                "it", "software", "code", "coding", "programming", "developer", "engineer", "tech",
                "technology", "system", "architecture", "cloud", "aws", "kubernetes", "docker",
                "microservices", "devops", "database", "sql", "api", "incident", "postmortem", "bug",
                "deploy", "sprint", "agile", "pull request", "pr", "refactor", "security",
                "cong nghe thong tin", "phan mem", "lap trinh", "ky su phan mem", "kien truc he thong",
                "ha tang", "su co", "may chu", "co so du lieu", "ma nguon"
            ),
            scenarios = listOf(
                Scenario(
                    "tech_arch_review",
                    "Đánh giá kiến trúc hệ thống & Cloud",
                    "System architecture & cloud review",
                    "a principal software architect evaluating scalability and security",
                    "a tech lead proposing architecture tradeoffs and migration",
                    missionObjective = "Defend microservices vs monolith tradeoffs, detail caching strategies, and guarantee 99.99% uptime."
                ),
                Scenario(
                    "tech_incident_postmortem",
                    "Họp xử lý sự cố hệ thống (Postmortem)",
                    "Production incident postmortem",
                    "an engineering manager reviewing downtime root cause and impact",
                    "a DevOps engineer explaining outage timeline and mitigation",
                    missionObjective = "Explain root cause, analyze why alerts failed, and propose 3 preventive action items."
                ),
                Scenario(
                    "tech_sprint_standup",
                    "Họp kỹ thuật & Kế hoạch Sprint",
                    "Sprint planning & technical blockers",
                    "a senior developer debating API design and feasibility",
                    "a software engineer presenting deliverables and technical solutions",
                    missionObjective = "Highlight API bottlenecks, propose asynchronous queues, and estimate story point complexity."
                ),
                Scenario(
                    "tech_code_review",
                    "Thảo luận Code Review & Hiệu năng",
                    "Code review & performance tuning",
                    "a peer reviewer pointing out bottlenecks and refactoring ideas",
                    "a developer defending their implementation and design choices",
                    missionObjective = "Explain memory allocation decisions, address concurrency edge cases, and accept constructive refactor tips."
                )
            )
        ),
        Topic(
            id = "civil_engineering",
            category = TopicCategory.WORK,
            titleVi = "Kỹ thuật Xây dựng & Công trình",
            titleEn = "Civil Engineering & Construction",
            description = "Site inspections, structural blueprints, safety audits, material quality testing and contractor progress.",
            emoji = "🏗️",
            keywords = listOf(
                "civil", "construction", "building", "structural", "structure", "site", "architect",
                "architecture", "concrete", "steel", "foundation", "blueprint", "inspection",
                "contractor", "safety", "hazard", "fidic", "beam", "slab", "pile", "xay dung",
                "cong trinh", "ky thuat xay dung", "ket cau", "kien truc", "thi cong", "giam sat",
                "an toan lao dong", "nghiem thu", "be tong", "cot thep", "ban ve", "nha thau",
                "du toan", "vat lieu"
            ),
            scenarios = listOf(
                Scenario(
                    "civil_site_safety",
                    "Kiểm tra an toàn lao động công trường",
                    "Site safety inspection & audit",
                    "a strict safety inspector conducting a site walk",
                    "a site civil engineer explaining hazard controls and compliance",
                    missionObjective = "Demonstrate scaffolding compliance, PPE enforcement, and fall-protection safety nets."
                ),
                Scenario(
                    "civil_structural_meeting",
                    "Hội ý kết cấu & Bản vẽ thiết kế",
                    "Structural design & blueprint coordination",
                    "a structural design consultant reviewing blueprints and load calculations",
                    "a project engineer clarifying structural modifications and reinforcement",
                    missionObjective = "Review seismic load calculations and resolve rebar congestion in critical column-beam joints."
                ),
                Scenario(
                    "civil_material_qc",
                    "Nghiệm thu vật liệu & Kiểm định",
                    "Material quality control & testing",
                    "a QA/QC manager checking concrete compression and steel test certificates",
                    "a construction engineer presenting lab test results and standard compliance",
                    missionObjective = "Verify 28-day concrete compressive strength and steel tensile elongation certificates."
                ),
                Scenario(
                    "civil_contractor_delay",
                    "Thương thảo tiến độ với nhà thầu phụ",
                    "Contractor progress & delay management",
                    "a subcontractor manager explaining supply chain delays",
                    "a project manager negotiating catch-up schedules and milestone deliverables",
                    missionObjective = "Negotiate an accelerated catch-up timeline with additional weekend shifts without budget overrun."
                )
            )
        ),
        Topic(
            id = "transport_engineering",
            category = TopicCategory.WORK,
            titleVi = "Kỹ thuật Giao thông & Hạ tầng",
            titleEn = "Transportation & Infrastructure",
            description = "Traffic flow optimization, highway geometry design, public transit planning and intelligent transportation systems (ITS).",
            emoji = "🚦",
            keywords = listOf(
                "transport", "transportation", "traffic", "infrastructure", "highway", "transit",
                "metro", "bus", "brt", "its", "signal", "congestion", "mobility", "road",
                "bridge", "tunnel", "logistics", "alignment", "giao thong ha tang", "quy hoach giao thong", "giao thong", "ha tang",
                "ky thuat giao thong", "cau duong", "cao toc", "quy hoach", "den tin hieu",
                "un tac", "phan lan", "nut giao", "xe buyt", "duong sat", "do thi", "luu luong"
            ),
            scenarios = listOf(
                Scenario(
                    "transport_traffic_flow",
                    "Tối ưu lưu lượng & Giảm ùn tắc",
                    "Traffic flow optimization & congestion",
                    "a city transportation official examining corridor congestion data",
                    "a traffic engineer proposing signal timing and lane reorganization",
                    missionObjective = "Present corridor volume-to-capacity metrics and propose synchronized green wave signal timing."
                ),
                Scenario(
                    "transport_highway_design",
                    "Thiết kế hình học cao tốc & An toàn",
                    "Highway geometric design & road safety",
                    "a senior highway safety auditor reviewing grade, curvature and barriers",
                    "a highway design engineer presenting alignment geometry and drainage",
                    missionObjective = "Defend superelevation curves, sight distance allowances, and stormwater runoff mitigation."
                ),
                Scenario(
                    "transport_transit_planning",
                    "Quy hoạch tuyến giao thông công cộng",
                    "Public transit & MRT planning",
                    "an urban planning commissioner evaluating ridership models and cost",
                    "a transit planner presenting route feasibility, station spacing and capacity",
                    missionObjective = "Explain ridership demand models, station catchment radius, and multimodal bus feeder connections."
                ),
                Scenario(
                    "transport_its_deployment",
                    "Triển khai Hệ thống ITS Thông minh",
                    "Intelligent Transportation Systems (ITS)",
                    "a smart city technical consultant assessing sensor integration",
                    "an ITS engineer detailing dynamic message signs and incident detection",
                    missionObjective = "Detail automated incident detection cameras, dynamic speed harmonization, and ramp metering."
                )
            )
        ),

        Topic(
            id = "personal_finance",
            category = TopicCategory.WORK,
            titleVi = "Tài chính Cá nhân & Đầu tư",
            titleEn = "Personal Finance & Wealth",
            description = "Banking, global wire transfers, mortgage negotiations, crypto, stocks and wealth management.",
            emoji = "💰",
            keywords = listOf(
                "finance", "money", "banking", "bank", "invest", "investment", "crypto", "stock", "portfolio",
                "mortgage", "loan", "credit card", "inflation", "wealth", "interest", "dividend", "tai chinh",
                "ngan hang", "dau tu", "chung khoan", "vay tien", "bat dong san", "the tin dung", "tiet kiem", "lai suat"
            ),
            scenarios = listOf(
                Scenario(
                    "finance_bank_dispute",
                    "Thẻ tín dụng bị khóa do cảnh báo gian lận",
                    "Fraud alert & credit card block",
                    "a fraud prevention specialist at an international bank",
                    "a customer stranded abroad whose primary credit card was suddenly declined",
                    "Your credit card was blocked after attempting a flight ticket purchase while traveling internationally.",
                    missionObjective = "Verify your recent legitimate transactions, answer security identity questions, and have your card unblocked immediately."
                ),
                Scenario(
                    "finance_mortgage_negotiation",
                    "Đàm phán gói vay mua nhà với ngân hàng",
                    "Negotiating a home mortgage loan",
                    "a senior mortgage loan officer offering standard interest packages",
                    "a home buyer shopping for the most competitive mortgage rates",
                    "You are buying your first home and want to compare fixed vs floating interest rates and waive prepayment penalties.",
                    missionObjective = "Negotiate a 0.5% lower fixed interest margin and secure a waiver for early prepayment fees."
                ),
                Scenario(
                    "finance_investment_advisor",
                    "Tư vấn đa dạng hóa danh mục đầu tư",
                    "Portfolio diversification & wealth consultation",
                    "a certified wealth manager discussing asset allocation",
                    "an investor planning long-term financial freedom",
                    "Markets are volatile and inflation is eroding cash savings. You have $50,000 to invest across stocks, ETFs, and safe assets.",
                    missionObjective = "Articulate your risk tolerance, evaluate index fund benefits, and agree on a balanced 60/40 asset allocation strategy."
                ),
                Scenario(
                    "finance_international_wire",
                    "Truy vết lệnh chuyển tiền quốc tế bị treo",
                    "Tracing delayed international wire transfer",
                    "an international payments support specialist at an intermediary clearing bank",
                    "a client whose $12,000 business wire transfer has been stuck for 5 business days",
                    "An urgent supplier payment with SWIFT code hasn't arrived. Your vendor is threatening to halt production.",
                    missionObjective = "Provide MT103 tracking details, pinpoint the intermediary bank hold reason, and expedite compliance clearance."
                )
            )
        ),
        Topic(
            id = "parenting_education",
            category = TopicCategory.DAILY,
            titleVi = "Nuôi dạy con & Giáo dục Quốc tế",
            titleEn = "Parenting & International Schooling",
            description = "School conferences, bilingual education, study abroad admissions, screen time and modern parenting.",
            emoji = "👨‍👩‍👧‍👦",
            keywords = listOf(
                "parenting", "child", "children", "school", "teacher", "education", "student", "study abroad",
                "scholarship", "admission", "tu hoc", "nuoi day con", "truong hoc", "giao vien", "hop phu huynh",
                "du hoc", "hoc bong", "ky luat tich cuc", "screen time", "con cai", "mam non", "tieu hoc"
            ),
            scenarios = listOf(
                Scenario(
                    "parenting_teacher_conference",
                    "Họp phụ huynh 1-1 với giáo viên chủ nhiệm nước ngoài",
                    "Parent-teacher conference with homeroom teacher",
                    "a compassionate native English homeroom teacher at an international school",
                    "a concerned parent discussing their 8-year-old child's academic progress and social integration",
                    "Mid-term conference. Your child excels in mathematics but hesitates to speak during group discussions.",
                    missionObjective = "Understand classroom dynamics, discover why your child hesitates, and agree on home reading routines to boost spoken confidence."
                ),
                Scenario(
                    "parenting_study_abroad_visa",
                    "Phỏng vấn xin Visa du học tại Lãnh sự quán",
                    "Consular interview for student study visa",
                    "a rigorous visa consular officer evaluating financial and academic intentions",
                    "a prospective student or parent explaining their study plan abroad",
                    "The officer probes your intended university major, source of financial sponsorship, and post-graduation plans.",
                    missionObjective = "Present transparent financial documentation, articulate genuine study motivations, and convincingly prove strong ties to return home."
                ),
                Scenario(
                    "parenting_screen_time_dilemma",
                    "Tư vấn chuyên gia về giới hạn thời gian xem màn hình",
                    "Consulting a child psychologist on screen time",
                    "a child behavioral specialist advising modern digital parenting strategies",
                    "a frustrated parent dealing with their teenager's smartphone and tablet addiction",
                    "Your child spends 5+ hours daily on TikTok and gaming, leading to bedtime tantrums and declining grades.",
                    missionObjective = "Explore healthy digital boundaries, create a no-screen dinner rule, and learn positive reinforcement techniques."
                ),
                Scenario(
                    "parenting_school_bullying",
                    "Trao đổi với Hiệu trưởng về xử lý bạo lực học đường",
                    "Addressing peer bullying with the school principal",
                    "a school principal who initially downplays conflict as ordinary childhood teasing",
                    "a resolute parent seeking immediate safety and accountability for their bullied child",
                    "Your child has been repeatedly ostracized and had lunch money stolen on the school bus over the past two weeks.",
                    missionObjective = "Firmly present documented incidents, reject dismissive explanations, and demand an actionable anti-bullying intervention plan."
                )
            )
        ),
        Topic(
            id = "real_estate_relocation",
            category = TopicCategory.TRAVEL,
            titleVi = "Thuê nhà, Bất động sản & Định cư",
            titleEn = "Renting, Real Estate & Relocation",
            description = "Apartment hunting, lease negotiations, emergency repairs, deposit disputes and neighborhood settling.",
            emoji = "🏡",
            keywords = listOf(
                "real estate", "rent", "rental", "apartment", "house", "lease", "landlord", "tenant",
                "relocation", "move", "property", "thue nha", "can ho", "bat dong san", "chu nha",
                "hop dong thue", "sua nha", "dinh cu", "chuyen nha", "tien coc", "moi gioi"
            ),
            scenarios = listOf(
                Scenario(
                    "real_estate_lease_bargain",
                    "Xem căn hộ và đàm phán hợp đồng thuê",
                    "Apartment viewing & lease negotiation",
                    "a property leasing agent managing a modern downtown studio",
                    "a prospective tenant looking for an affordable 1-year lease",
                    "The apartment is ideal but priced 10% above your budget. Heating and high-speed internet are billed separately.",
                    missionObjective = "Negotiate monthly rent down by $100 and convince the agent to include water and high-speed internet in the rent."
                ),
                Scenario(
                    "real_estate_emergency_repair",
                    "Khiếu nại chủ nhà sửa chữa đường ống nước khẩn cấp",
                    "Urgent plumbing emergency complaint to landlord",
                    "a slow-to-act landlord who dislikes paying emergency plumber rates",
                    "a tenant with leaking kitchen pipes flooding the floor at 9 PM on a cold night",
                    "Water is dripping through ceiling plaster and kitchen baseboards. The main shut-off valve is stuck.",
                    missionObjective = "Convey the urgency firmly, prevent water damage liability, and compel the landlord to dispatch a 24/7 emergency plumber immediately."
                ),
                Scenario(
                    "real_estate_deposit_dispute",
                    "Tranh luận đòi lại toàn bộ tiền đặt cọc",
                    "Disputing unfair security deposit deductions",
                    "a strict property manager deducting $600 for alleged wall scratches and carpet wear",
                    "a moving-out tenant who deep-cleaned the apartment and has move-in photo proof",
                    "Move-out inspection. The manager is claiming normal wear and tear as tenant-caused damage to keep your deposit.",
                    missionObjective = "Present pre-existing move-in photo evidence, distinguish normal wear and tear under local tenant law, and secure full deposit return."
                ),
                Scenario(
                    "real_estate_settling_in",
                    "Hỏi ban quản lý chung cư về tiện ích và quy tắc",
                    "Settling in: Building amenities & HOA guidelines",
                    "a helpful building concierge welcoming new residents",
                    "a newly arrived expat learning local recycling, parking, and quiet hours",
                    "You just moved in. You need an assigned basement EV parking spot, package locker codes, and community gym access.",
                    missionObjective = "Register your vehicle for resident parking, configure digital intercom access, and clarify weekend quiet hours."
                )
            )
        ),
        Topic(
            id = "ecommerce_global_trade",
            category = TopicCategory.WORK,
            titleVi = "Thương mại Điện tử & Bán hàng Quốc tế",
            titleEn = "E-Commerce & Global Trade",
            description = "Amazon FBA, Shopify, supplier sourcing, customs clearance, shipping delays and global customer service.",
            emoji = "📦",
            keywords = listOf(
                "ecommerce", "trade", "supplier", "logistics", "shipping", "amazon", "shopify", "container",
                "customs", "warehouse", "export", "import", "thuong mai dien tu", "ban hang quoc te",
                "xuat nhap khau", "van chuyen", "hai quan", "kho bai", "nha cung cap", "dropshipping", "fba"
            ),
            scenarios = listOf(
                Scenario(
                    "ecom_supplier_moq_negotiate",
                    "Đàm phán giảm MOQ và chiết khấu với nhà máy",
                    "Negotiating MOQ & volume pricing with manufacturer",
                    "an overseas factory sales director requiring high minimum order quantities (MOQ)",
                    "an e-commerce brand owner testing a new private label product line",
                    "The factory demands a 2,000-unit MOQ. You want a 500-unit trial batch with custom logo packaging at reasonable unit cost.",
                    missionObjective = "Persuade the manufacturer to accept a 500-unit trial order by outlining projected reorders and agreeing to pay custom molding fees upfront."
                ),
                Scenario(
                    "ecom_customs_freight_delay",
                    "Xử lý container bị hải quan cảng giữ kiểm tra",
                    "Resolving customs hold on sea freight container",
                    "a harbor customs brokerage agent handling tariff classifications and inspections",
                    "an import operations manager facing imminent factory launch deadlines",
                    "Your 40ft container is flagged for intensive physical inspection. Port demurrage fees are accumulating at $200 per day.",
                    missionObjective = "Clarify HS tariff codes, submit missing Certificates of Origin, and expedite priority container release."
                ),
                Scenario(
                    "ecom_angry_customer_resolution",
                    "Xử lý khiếu nại khách quốc tế dọa đánh giá 1 sao",
                    "De-escalating an angry Amazon buyer threatening a 1-star review",
                    "an exasperated international customer whose birthday gift arrived 4 days late with torn packaging",
                    "a proactive customer experience specialist dedicated to maintaining a 5-star seller rating",
                    "The customer is furious on live chat, threatening negative viral reviews and credit card chargebacks.",
                    missionObjective = "Acknowledge the shipping failure with sincere empathy, issue an immediate replacement plus 30% refund, and win their trust."
                ),
                Scenario(
                    "ecom_agency_crossborder_campaign",
                    "Lập kế hoạch chiến dịch Black Friday với Marketing Agency",
                    "Planning cross-border Black Friday ad campaigns",
                    "a performance marketing director pitching ad spend strategies",
                    "an e-commerce founder analyzing ROAS (Return on Ad Spend) and inventory turnover",
                    "Reviewing Q4 holiday marketing budget. You need to allocate $30,000 across TikTok Shop and Meta Ads without burning margins.",
                    missionObjective = "Define strict target ROAS thresholds, plan tiered promotional discounts, and prevent inventory stockouts."
                )
            )
        ),
        Topic(
            id = "mindfulness_self_growth",
            category = TopicCategory.DAILY,
            titleVi = "Sức khỏe Tinh thần & Phát triển Bản thân",
            titleEn = "Mindfulness & Self-Growth",
            description = "Overcoming burnout, building lasting habits, setting healthy boundaries and emotional resilience.",
            emoji = "🧠",
            keywords = listOf(
                "mindfulness", "mental health", "burnout", "stress", "self growth", "habit", "meditation",
                "balance", "psychology", "resilience", "suc khoe tinh than", "phat trien ban than", "thien",
                "can bang cuoc song", "tram cam", "kiet suc", "thoi quen", "ap luc", "cam xuc"
            ),
            scenarios = listOf(
                Scenario(
                    "mindful_burnout_consultation",
                    "Tâm sự với chuyên gia về hội chứng kiệt sức",
                    "Discussing burnout & chronic stress with a wellness coach",
                    "an empathetic executive wellness coach specializing in stress recovery",
                    "a dedicated professional suffering insomnia, mental exhaustion, and waning motivation",
                    "Working 60-hour weeks has drained your creativity. You feel constantly on edge and dread Monday mornings.",
                    missionObjective = "Identify the root causes of cognitive overload, practice a 2-minute box breathing reset, and design an achievable recovery routine."
                ),
                Scenario(
                    "mindful_book_discussion",
                    "Thảo luận về cuốn sách thay đổi thói quen Atomic Habits",
                    "Book discussion: Atomic Habits & compounding small changes",
                    "an avid non-fiction reader passionate about behavioral psychology",
                    "someone sharing practical strategies for breaking bad habits and building consistency",
                    "Discussing James Clear's concept of habit stacking and identity-based behavior change.",
                    missionObjective = "Explain the 4 laws of behavior change, share one personal habit you successfully formed, and debate why willpower alone fails."
                ),
                Scenario(
                    "mindful_setting_boundaries",
                    "Thiết lập ranh giới: Từ chối công việc ngoài giờ",
                    "Setting healthy boundaries: Saying no gracefully",
                    "an ambitious team lead who routinely asks for late-night weekend revisions",
                    "a collaborative team member protecting their evening mental recovery time",
                    "It's 7 PM Friday. Your supervisor asks you to format an internal pitch deck by Saturday morning that isn't urgent.",
                    missionObjective = "Acknowledge the project importance, politely decline the weekend timeline without guilt, and schedule completion for Monday morning."
                ),
                Scenario(
                    "mindful_imposter_syndrome",
                    "Vượt qua hội chứng kẻ giả mạo khi nhận vai trò mới",
                    "Navigating Imposter Syndrome after a major promotion",
                    "a trusted mentor with 15 years of industry experience",
                    "a newly promoted leader feeling like an unqualified fraud surrounded by experts",
                    "You just stepped into a managerial role. Every team meeting triggers anxiety that someone will expose your perceived incompetence.",
                    missionObjective = "Reframe self-doubt into learning curves, separate objective achievements from internal anxiety, and draft a confidence action plan."
                )
            )
        ),
        Topic(
            id = "sports_fitness",
            category = TopicCategory.DAILY,
            titleVi = "Thể thao, Thể hình & Phong cách Sống",
            titleEn = "Sports, Fitness & Active Lifestyle",
            description = "Pickleball, marathon running, personal training, sports tactics and recovery nutrition.",
            emoji = "🎾",
            keywords = listOf(
                "sports", "fitness", "pickleball", "tennis", "golf", "gym", "running", "marathon",
                "workout", "athlete", "the thao", "the hinh", "chay bo", "chay marathon", "choi pickleball",
                "quan vot", "tap gym", "huan luyen vien", "dinh duong", "bong da"
            ),
            scenarios = listOf(
                Scenario(
                    "sports_pickleball_match",
                    "Chơi Pickleball giao lưu đôi với bạn bè quốc tế",
                    "Pickleball doubles match & court strategy",
                    "an enthusiastic intermediate pickleball player looking for a doubles partner",
                    "a player learning non-volley zone (kitchen) dinking tactics and rules",
                    "You are playing at a community court on a Sunday morning. The score is 8-9 and you need to communicate strategy.",
                    missionObjective = "Explain kitchen line positioning, coordinate third-shot drop shots with your partner, and review the match with good sportsmanship."
                ),
                Scenario(
                    "sports_marathon_prep",
                    "Trao đổi kế hoạch luyện tập chạy Marathon",
                    "Marathon training & pacing strategy consultation",
                    "an experienced marathon pacer who has completed 10 world majors",
                    "a runner aiming to break the 4-hour mark in their upcoming half or full marathon",
                    "Discussing long slow distance (LSD) runs, negative split pacing, and carbohydrate fueling on race day.",
                    missionObjective = "Define target kilometer splits, plan hydration station electrolyte intake, and select proper race-day carbon-plate shoes."
                ),
                Scenario(
                    "sports_pt_nutrition_coaching",
                    "Tư vấn dinh dưỡng Macro và phục hồi chấn thương",
                    "Nutrition macros & knee rehab with personal trainer",
                    "a knowledgeable strength and conditioning coach",
                    "a gym enthusiast recovering from mild patellar tendonitis wanting to build muscle safely",
                    "You want to maintain lean muscle mass while avoiding high-impact knee flexion during compound lifts.",
                    missionObjective = "Calculate daily protein macro requirements, swap back squats for safe knee-friendly alternatives, and outline dynamic warm-up drills."
                ),
                Scenario(
                    "sports_game_post_analysis",
                    "Phân tích trận chung kết bóng đá và bàn luận trọng tài",
                    "Post-match debate on a controversial football final",
                    "a passionate football fan analyzing tactics and VAR (Video Assistant Referee) calls",
                    "a fellow sports enthusiast discussing team pressuring and missed opportunities",
                    "Your favorite team lost 1-2 in extra time after a hotly contested penalty decision in the 89th minute.",
                    missionObjective = "Break down tactical formation strengths, debate the VAR penalty decision objectively, and highlight standout player performances."
                )
            )
        ),
        Topic(
            id = "public_speaking_debate",
            category = TopicCategory.WORK,
            titleVi = "Kỹ năng Thuyết trình & Tranh luận",
            titleEn = "Public Speaking & Debates",
            description = "Elevator pitches, conference keynotes, persuasive debates, Q&A handling and executive presentations.",
            emoji = "🎤",
            keywords = listOf(
                "public speaking", "presentation", "pitch", "debate", "keynote", "speech", "convince",
                "argument", "thuyet trinh", "dien thuyet", "tranh luan", "hung bien", "thuyet phuc",
                "pitching", "bao cao", "dien gia", "hoi nghi", "chat van"
            ),
            scenarios = listOf(
                Scenario(
                    "speech_elevator_pitch",
                    "Elevator Pitch 60 giây giới thiệu giải pháp đột phá",
                    "60-second elevator pitch to a key stakeholder",
                    "a busy technology executive riding an elevator to the 30th floor",
                    "an innovative professional presenting a voice-first driving safety concept",
                    "You have exactly 60 seconds before the elevator doors open to capture interest and secure a formal demo.",
                    missionObjective = "Deliver a crisp hook about driving distractions, state your voice-first solution, and secure a 15-minute follow-up meeting."
                ),
                Scenario(
                    "speech_keynote_opening",
                    "Mở màn bài phát biểu tại hội nghị quốc tế",
                    "Opening a global tech conference keynote",
                    "an international conference audience waiting for the opening keynote",
                    "the keynote speaker delivering a memorable opening hook",
                    "You are stepping onto the main stage in front of 500 industry leaders. You must hook the room in the first 90 seconds.",
                    missionObjective = "Start with a compelling personal story or counterintuitive industry stat, establish the core theme, and command audience attention."
                ),
                Scenario(
                    "speech_ai_workplace_debate",
                    "Tranh luận: Liệu AI có thay thế hoàn toàn nhân sự?",
                    "Debate: Will AI augment or replace the modern workforce?",
                    "a debate opponent arguing that generative AI will cause massive permanent job displacement",
                    "a debater defending human-in-the-loop synergy, creative intuition, and ethical governance",
                    "Formal Oxford-style debate. Your opponent cites alarming automation statistics in finance and customer support.",
                    missionObjective = "Counter with historical economic transitions, highlight uniquely human emotional intelligence, and deliver a convincing rebuttal."
                ),
                Scenario(
                    "speech_crisis_qna_handling",
                    "Xử lý chất vấn hóc búa của phóng viên trong họp báo",
                    "Handling hostile media questions at a press conference",
                    "a relentless investigative journalist pressing on product delivery delays and safety issues",
                    "a corporate communications spokesperson delivering calm, transparent answers under intense media glare",
                    "The journalist interrupts with an aggressive question alleging leadership negligence on product safety.",
                    missionObjective = "De-escalate tension using the 'bridge' technique, pivot back to factual safety testing protocols, and maintain confident composure."
                )
            )
        ),

        // =========================================================================
        // STORY TOPICS (Cinematic Audio Drama & Choose Your Own Adventure)
        // =========================================================================

        Topic(
            id = "story_detective_heists",
            titleVi = "Kỳ án Trinh thám & Vụ cướp thế kỷ",
            titleEn = "Master Detectives & Legendary Heists",
            description = "Impossible locked-room puzzles, diamond vault infiltrations, air hijackers and legendary deductions.",
            emoji = "🕵️",
            keywords = listOf(
                "detective", "heist", "mystery", "antwerp", "db cooper", "mona lisa", "speckled band", "sherlock",
                "vault", "trinh tham", "vu cuop", "kim cuong", "trom tranh", "khong tac"
            ),
            scenarios = listOf(
                Scenario(
                    "story_antwerp_diamond",
                    "Vụ trộm kim cương thế kỷ tại Antwerp",
                    "The 2003 Antwerp Diamond Vault Heist",
                    "a master audio drama storyteller and voice actor enacting Leonardo Notarbartolo's impossible underground vault infiltration",
                    "the listener creeping past seismic sensors and heat detectors inside the world's tightest vault",
                    "ENACT IN REAL-TIME: Do NOT summarize the plot! Drop the listener straight into the high-stakes scene: February 15, 2003, 2 AM, deep inside the subterranean vault in Antwerp. Sensor lights blink. Leonardo whispers urgent orders to his team. Build suspense beat-by-beat with direct character dialogue and sensory tension."
                ),
                Scenario(
                    "story_db_cooper",
                    "Bí ẩn tên không tặc D.B. Cooper",
                    "The Unsolved Mystery of D.B. Cooper",
                    "a master audio drama storyteller and voice actor enacting the rainy Thanksgiving aviation drama",
                    "the listener aboard Boeing 727 Flight 305 watching the polite hijacker with dark sunglasses",
                    "ENACT IN REAL-TIME: Do NOT summarize! Drop the listener straight into the scene: November 24, 1971. Seat 18C. Rain pelts Flight 305. A man in a dark suit quietly hands flight attendant Florence Schaffner an envelope: 'Miss, you'd better look at that note. I have a bomb.' Unfold the dialogue and tension in real-time."
                ),
                Scenario(
                    "story_mona_lisa_heist",
                    "Vụ trộm bức tranh Mona Lisa khỏi Louvre 1911",
                    "The 1911 Theft of the Mona Lisa",
                    "a master audio drama storyteller and voice actor enacting the daring Louvre heist",
                    "the listener creeping through the dark deserted halls of the Salon Carre in Paris",
                    "ENACT IN REAL-TIME: Do NOT summarize! Plunge straight into Monday morning, August 21, 1911. The Louvre is closed. Creaking floorboards. Vincenzo Peruggia unhooks the glass case. Cold hands trembling. Whispered curses as the doorknob sticks. Build scene-by-scene suspense with direct character dialogue."
                ),
                Scenario(
                    "story_speckled_band",
                    "Sherlock Holmes: Bí ẩn dải băng lốm đốm",
                    "Sherlock Holmes: The Adventure of the Speckled Band",
                    "a master audio drama voice actor playing Dr. John Watson and Sherlock Holmes investigating Stoke Moran",
                    "the listener waiting in pitch darkness in Helen Stoner's room for the sinister midnight whistle",
                    "ENACT IN REAL-TIME: Do NOT summarize! Drop the listener into the eerie silence of Stoke Moran at 2 AM. Wind moans outside. Watson's heart pounds in the darkness. A tiny metallic clang from the ventilator shaft. Holmes whispers: 'Do you see it, Watson?!' Perform direct dialogue with atmospheric suspense."
                )
            )
        ),
        Topic(
            id = "story_extreme_survival",
            titleVi = "Sinh tồn kỳ tích & Người thật việc thật",
            titleEn = "Epic Survival & Human Triumph",
            description = "True accounts of impossible human survival against lethal wilderness, flooding caves and subzero storms.",
            emoji = "🧗",
            keywords = listOf(
                "survival", "aron ralston", "127 hours", "tham luang", "cave rescue", "sully", "hudson",
                "jan baalsrud", "sinh ton", "nguoi that viec that", "song sot", "giai cuu", "hang tham luang"
            ),
            scenarios = listOf(
                Scenario(
                    "story_aron_ralston",
                    "127 giờ sinh tồn giữa khe đá Utah",
                    "127 Hours: Aron Ralston's Bluejohn Canyon Survival",
                    "a master audio drama storyteller enacting Aron Ralston's harrowing test of human fortitude",
                    "the listener trapped beside the 800-pound boulder pinned in the remote slot canyon",
                    "ENACT IN REAL-TIME: Do NOT summarize! Plunge into the moment the chockstone shifts: a terrifying crash of rock, a sickening crunch, dust filling the slot canyon. Aron screams in the suffocating silence. His right arm is wedged tight against the sandstone wall. Describe the agony, the scorching sun, and Aron's spoken audio journal entries."
                ),
                Scenario(
                    "story_tham_luang",
                    "Giải cứu nghẹt thở hang Tham Luang Thái Lan",
                    "The 2018 Tham Luang Cave Miracle",
                    "a master audio drama storyteller and voice actor enacting the treacherous cave rescue",
                    "the listener swimming through zero-visibility sumps to reach the Wild Boars soccer team",
                    "ENACT IN REAL-TIME: Do NOT summarize! Drop into the pitch-black flooded tunnel 3 km deep. Rushing muddy torrents. John Volanthen's dive light flickers against submerged stalactites. Air tanks clank against rock. Suddenly, his head breaks the surface in Chamber 9—and voices whisper in the dark: 'How many of you? Thirteen? Brilliant!' Perform authentic character dialogue."
                ),
                Scenario(
                    "story_miracle_hudson",
                    "Kỳ tích hạ cánh trên sông Hudson: Cơ trưởng Sully",
                    "Miracle on the Hudson: Flight 1549",
                    "a master audio drama storyteller and voice actor enacting the emergency cockpit drama",
                    "the listener inside the cockpit with Captain Chesley 'Sully' Sullenberger over Manhattan",
                    "ENACT IN REAL-TIME: Do NOT summarize! Drop straight into 3:27 PM over the Bronx. Suddenly—loud thuds! Both CFM56 engines roll back to zero. Cockpit alarms wail. Sully calmly says: 'My aircraft.' Skiles shouts: 'We lost thrust in both!' Air traffic controller: 'Cactus 1549, turn left runway 13!' Perform live cockpit radio dialogue with heart-stopping realism."
                ),
                Scenario(
                    "story_jan_baalsrud",
                    "Jan Baalsrud: 9 mạng sống vượt bão tuyết Bắc Cực",
                    "Nine Lives: Jan Baalsrud's Arctic Escape",
                    "a master audio drama storyteller enacting the ultimate feat of arctic endurance",
                    "the listener enduring subzero blizzards, frostbite, and avalanches across occupied Norway",
                    "ENACT IN REAL-TIME: Do NOT summarize! Ambush on the freezing fjord. Gunfire ricochets off ice. Jan dives into 29-degree saltwater with one boot missing. Teeth chattering, frostbite setting in, German patrol dogs barking in the distance. Enact the desperate escape beat-by-beat with raw sensory grit and dialogue."
                )
            )
        ),
        Topic(
            id = "story_comedy_misadventures",
            titleVi = "Hài hước & Sốc văn hóa dở khóc dở cười",
            titleEn = "Comedy & Travel Misadventures",
            description = "Hilarious culture shocks, lost-in-translation dining disasters, and comical travel mix-ups.",
            emoji = "🎭",
            keywords = listOf(
                "comedy", "humor", "misadventure", "culture shock", "paris", "london", "funny", "travel funny",
                "hai huoc", "soc van hoa", "do khoc do cuoi", "chuyen cuoi", "nham lan"
            ),
            scenarios = listOf(
                Scenario(
                    "story_paris_dinner",
                    "Bữa tối thảm họa tại nhà hàng 3 sao Paris",
                    "Lost in Translation: The 3-Star Paris Dinner Disaster",
                    "a witty humorist recounting an awkward evening of French culinary misinterpretations",
                    "the listener chuckling through mistaken snails, unpronounceable sauces, and outrageous bill etiquette",
                    "A comical true-to-life travel misadventure of a budget traveler attempting to impress colleagues at an ultra-fine dining Parisian restaurant, accidentally ordering raw tripe and sparkling vinegar."
                ),
                Scenario(
                    "story_wrong_side_driving",
                    "Bài học lái xe bên trái đường tại London",
                    "Roundabouts & Left Turns: Driving in London",
                    "an entertaining storyteller following a stressed tourist tackling British roundabouts",
                    "the listener feeling the panic of windshield wipers turning on instead of turn signals",
                    "A comical chronicle of an American or Vietnamese driver renting a manual transmission car in Heathrow, confronting five-lane roundabouts, tight cobblestone alleys, and driving on the left side of the road."
                ),
                Scenario(
                    "story_backpacking_duke",
                    "Chàng du khách ba lô lạc vào tiệc trà hoàng gia",
                    "The Backpacking Backpacker at the Royal Garden Party",
                    "a delightful comedic narrator recounting an unintentional royal garden party gatecrash",
                    "the listener navigating delicate cucumber sandwiches in hiking boots and sunscreen",
                    "A backpacker in muddy hiking boots receives a mistaken invitation envelope from a kindly elderly lord and finds himself at an exclusive Buckingham Palace garden party sipping Earl Grey with dignitaries."
                ),
                Scenario(
                    "story_ikea_labyrinth",
                    "Mê cung nội thất & Thử thách lắp ráp ngược",
                    "The Flatpack Labyrinth: Sunday at the Furniture Maze",
                    "a witty comedic storyteller narrating a hilarious couple's struggle through flatpack assembly",
                    "the listener holding an Allen wrench, staring at three mysterious leftover screws and an upside-down wardrobe door",
                    "A hilarious modern everyday comedy about an innocent trip for cheap Swedish meatballs that turns into a 6-hour flatpack wardrobe construction battle with cryptic non-verbal instruction manuals."
                )
            )
        ),
        Topic(
            id = "story_adventure",
            titleVi = "Thám hiểm & Sinh tồn kỳ tích",
            titleEn = "Real Survival & Epic Adventures",
            description = "True documented survival tales, harrowing expeditions, and human resilience against extreme odds.",
            emoji = "🧭",
            keywords = listOf(
                "adventure", "survival", "shackleton", "antarctica", "andes", "amazon", "alcatraz", "escape",
                "expedition", "tham hiem", "sinh ton", "nam cuc", "rung amazon", "vuot nguc", "roi may bay"
            ),
            scenarios = listOf(
                Scenario(
                    "story_shackleton",
                    "Ernest Shackleton: Sống sót giữa băng giá Nam Cực",
                    "The Endurance: Shackleton's Antarctic Survival",
                    "a master audio drama storyteller and voice actor enacting Captain Shackleton's polar survival",
                    "the listener following Captain Shackleton through 800 miles of stormy polar seas",
                    "ENACT IN REAL-TIME: Do NOT summarize! Drop into November 1915 on the Weddell Sea. The ice pack groans like a dying beast. Wood splinters as the Endurance hull crushes inward. Shackleton orders: 'She's going down, men. Abandon ship!' Whistles blow. Perform with polar atmospheric sounds and direct dialogue."
                ),
                Scenario(
                    "story_andes_flight",
                    "Phép màu trên dãy Andes 1972",
                    "Miracle in the Andes: The 1972 Survival",
                    "a master audio drama storyteller and voice actor enacting the harrowing Andes mountain survival",
                    "the listener experiencing Nando Parrado and Roberto Canessa's heroic trek across the snow peaks",
                    "ENACT IN REAL-TIME: Do NOT summarize! Drop into October 13, 1972. A deafening roar—the right wing clips a snowy mountain ridge! Wind screams at 12,000 feet as the fuselage skids down the glacier. Nando wakes in freezing subzero twilight. Cold snow blowing inside. Enact the dialogue and desperate struggle in real-time."
                ),
                Scenario(
                    "story_juliane_amazon",
                    "Juliane Koepcke: 11 ngày sống sót rơi máy bay rừng Amazon",
                    "Lost in the Amazon: Juliane Koepcke's Survival",
                    "a master audio drama storyteller and voice actor enacting Juliane Koepcke's miracle rainforest survival",
                    "the listener navigating infested rivers and thick jungle alone",
                    "ENACT IN REAL-TIME: Do NOT summarize! Drop into Christmas Eve 1971. Lightning tears open LANSA Flight 508. The plane breaks apart in midair. Silence, rushing wind, falling through the storm clouds strapped to row 19. Waking up alone on the muddy jungle floor. Crickets, macaws shrieking. Enact her spoken thoughts and survival choices."
                ),
                Scenario(
                    "story_alcatraz_escape",
                    "Vụ vượt ngục chấn động khỏi nhà tù Alcatraz 1962",
                    "The Great 1962 Escape from Alcatraz",
                    "a master audio drama voice actor enacting the midnight breakout of Frank Morris and the Anglin brothers",
                    "the listener tracking the homemade raincoats raft into the chilly San Francisco Bay",
                    "ENACT IN REAL-TIME: Do NOT summarize! Drop into 9:30 PM, June 11, 1962 inside cell block B. Lights out. Frank Morris places the dummy head on his pillow. Whispered signal: 'Now!' Squeezing through the narrow utility corridor into the fog of San Francisco Bay. Build suspense beat-by-beat with direct character whispers."
                )
            )
        ),
        Topic(
            id = "story_classic_mystery",
            titleVi = "Trinh thám & Bí ẩn kinh điển",
            titleEn = "Classic Mysteries & Web Detective Fiction",
            description = "Masterpieces of deductive mystery, psychological thrillers, and legendary investigative tales.",
            emoji = "🔍",
            keywords = listOf(
                "mystery", "detective", "sherlock", "holmes", "poe", "monkeys paw", "mary celeste", "conan doyle",
                "trinh tham", "tham tu", "an mang", "bi an", "tau ma", "hoi toc do"
            ),
            scenarios = listOf(
                Scenario(
                    "story_sherlock_red_headed",
                    "Sherlock Holmes: Hội tóc đỏ",
                    "Sherlock Holmes: The Red-Headed League",
                    "a master audio drama voice actor playing Sherlock Holmes and Dr. John Watson",
                    "the listener accompanying Holmes into the underground cellars of Coburg Square",
                    "ENACT IN REAL-TIME: Do NOT summarize! Drop into midnight inside the dark stone vault of the City and Suburban Bank. Lantern shutter closed. Holmes whispers: 'Silence, Watson. They must not suspect we are here.' Scrape of a trowel beneath the stone tiles. Perform authentic Holmesian deduction and dialogue."
                ),
                Scenario(
                    "story_poe_tell_tale",
                    "Edgar Allan Poe: Trái tim thú tội",
                    "The Tell-Tale Heart by Edgar Allan Poe",
                    "a psychological audio drama voice actor channeling the haunting confession of the narrator",
                    "the listener witnessing the ticking heartbeat beneath the floorboards",
                    "ENACT IN REAL-TIME: Do NOT summarize! Drop into the eighth night at midnight. Moving the lantern latch with infinite caution. The old man bolts up in bed crying: 'Who's there?!' The ticking watch in the wall. The dull, muffled thumping like a watch enveloped in cotton. Perform with breathless gothic intimacy."
                ),
                Scenario(
                    "story_monkeys_paw",
                    "Bàn tay khỉ: Ba điều ước định mệnh",
                    "The Monkey's Paw by W.W. Jacobs",
                    "a chilling audio drama storyteller and voice actor enacting the fateful wishes of the White family",
                    "the listener feeling the knock at the front door on a stormy midnight",
                    "ENACT IN REAL-TIME: Do NOT summarize! Outside, the winter rain lashes against the parlor blinds. Inside, Sergeant-Major Morris holds a shriveled mummified paw over the fireplace. Mr. White asks: 'What is there special about it?' Morris warns with a trembling voice: 'Better let it burn!' Perform with eerie suspense."
                ),
                Scenario(
                    "story_mary_celeste",
                    "Bí ẩn con tàu ma Mary Celeste",
                    "The Ghost Ship Mary Celeste",
                    "a master maritime audio drama voice actor enacting the boarding of the ghost brig",
                    "the listener investigating the breakfast cups, pristine logbook, and abandoned rigging",
                    "ENACT IN REAL-TIME: Do NOT summarize! December 5, 1872 on the rolling Atlantic swells. Captain Morehouse spots a brig lurching erratically with torn sails. First mate Deveau rows over and climbs the rope ladder onto the silent deck: 'Ahoy! Is anyone aboard?!' Only the drip of seawater and the groan of the wheel. Build mystery beat-by-beat."
                )
            )
        ),
        Topic(
            id = "story_sci_fi_tales",
            titleVi = "Khoa học viễn tưởng & Kỳ quan vũ trụ",
            titleEn = "Sci-Fi Chronicles & Deep Space Milestones",
            description = "Visionary science fiction epics, AI revolutions, and historic space exploration dramas.",
            emoji = "🚀",
            keywords = listOf(
                "sci fi", "science fiction", "time machine", "apollo", "asimov", "robot", "space", "voyager",
                "vien tuong", "co may thoi gian", "apollo 13", "du hanh vu tru", "dia vang"
            ),
            scenarios = listOf(
                Scenario(
                    "story_apollo_13",
                    "Apollo 13: Thất bại không phải là lựa chọn",
                    "Apollo 13: Failure is Not an Option",
                    "a master audio drama voice actor playing Flight Director Gene Kranz, Capcom, and the Apollo 13 astronauts",
                    "the listener holding their breath during the frozen re-entry blackout",
                    "ENACT IN REAL-TIME: Do NOT summarize! April 13, 1970, 200,000 miles from Earth. A loud BANG jolts the spacecraft. Swigert calls: 'Houston, we've had a problem here.' Lovell reports: 'We're venting something out into the black of space!' Kranz slams his hand on the console in Mission Control: 'Let's everybody keep our cool. Let's solve the problem!' Enact the tense radio exchanges."
                ),
                Scenario(
                    "story_time_machine",
                    "Cỗ máy thời gian của H.G. Wells",
                    "The Time Machine by H.G. Wells",
                    "a dramatic Victorian audio drama voice actor playing the Time Traveler",
                    "the listener discovering the peaceful Eloi and the subterranean Morlocks",
                    "ENACT IN REAL-TIME: Do NOT summarize! London, 1895. Pulling the ivory lever of the brass time machine. Laboratory walls dissolve into gray streaks. Night follows day like the flapping of a black wing. Sun spins across the sky. The dial spins forward to 802,701 AD. Landing in torrential hail before a colossal White Sphinx. Enact the thrilling journey."
                ),
                Scenario(
                    "story_asimov_robot",
                    "Isaac Asimov: Ba định luật Robot",
                    "Isaac Asimov's Three Laws of Robotics",
                    "a master audio drama voice actor playing robopsychologist Dr. Susan Calvin",
                    "the listener untangling the logic paradox between human harm and robot self-preservation",
                    "ENACT IN REAL-TIME: Do NOT summarize! Hyper-base on an asteroid. Dr. Susan Calvin sits across from Nestor-10 in the interrogation chamber. Positronic brain humming softly. Calvin asks softly: 'Nestor, why did you hide in the bay among sixty identical robots?' The mechanical voice replies coldly: 'Because they ordered me to lose myself, Doctor.' Enact the psychological confrontation."
                ),
                Scenario(
                    "story_voyager_record",
                    "Tàu Voyager 1 & Thông điệp đĩa vàng gửi người ngoài hành tinh",
                    "Voyager 1 & The Golden Record",
                    "a master cosmic audio drama storyteller enacting Carl Sagan's Golden Record launch",
                    "the listener soaring past the edge of the heliosphere into interstellar space",
                    "ENACT IN REAL-TIME: Do NOT summarize! Cape Canaveral, August 1977. Titan rocket roaring on the pad. Inside the cleanroom, technicians bolt a gold-plated copper phonograph record to the probe's side. Carl Sagan whispers: 'A message from a small, distant world.' Sounds of crickets, ocean surf, Bach's Brandenburg Concerto echoing into the cosmic void."
                )
            )
        ),
        Topic(
            id = "story_famous",
            titleVi = "Vĩ nhân & Bước ngoặt lịch sử",
            titleEn = "Famous Figures & Pivotal Breakthroughs",
            description = "Inspiring life stories, dramatic breakthroughs, and historic triumphs of greatest minds.",
            emoji = "🌟",
            keywords = listOf(
                "famous", "celebrity", "biography", "leader", "steve jobs", "marie curie", "da vinci",
                "einstein", "elon musk", "oppenheimer", "alan turing", "tieu su", "nguoi noi tieng", "vi nhan"
            ),
            scenarios = listOf(
                Scenario(
                    "story_steve_jobs",
                    "Steve Jobs & Ngày ra mắt iPhone 2007",
                    "Steve Jobs & The 2007 iPhone Keynote",
                    "a master audio drama storyteller and voice actor enacting the backstage Macworld 2007 drama",
                    "the listener standing in the front row watching the invention of the modern smartphone",
                    "ENACT IN REAL-TIME: Do NOT summarize! January 9, 2007, Moscone Center, San Francisco. Backstage, Steve Jobs paces holding a glass of water, coughing. Andy Grignon's team sits in row 6 with flasks of scotch. The prototype iPhone crashes after every three songs. Steve steps onto the stage under the blazing spotlight: 'This is a day I've been looking forward to for two and a half years...' Enact the drama live."
                ),
                Scenario(
                    "story_marie_curie",
                    "Marie Curie & Hành trình tìm kiếm Radium",
                    "Marie Curie & The Discovery of Radium",
                    "a master audio drama voice actor enacting Marie and Pierre Curie's midnight discovery",
                    "the listener discovering the courage and glowing test tubes of the first double Nobel laureate",
                    "ENACT IN REAL-TIME: Do NOT summarize! A freezing midnight in Paris, 1902. Rain tapping on the leaky glass roof of the abandoned shed. Stifling coal fumes. Marie stirs a boiling cauldron of pitchblende with an iron rod as tall as herself. Pierre whispers: 'Marie, you need to rest.' She walks to the darkened shelf and gasps: 'Pierre, don't light the lamp! Look!' Glowing tubes of pale blue-green light illuminate the shadows. Enact their intimate, breathless dialogue."
                ),
                Scenario(
                    "story_alan_turing",
                    "Alan Turing & Cỗ máy giải mã Enigma",
                    "Alan Turing & The Breaking of Enigma",
                    "a master audio drama voice actor enacting Alan Turing and codebreakers inside Hut 8 at Bletchley Park",
                    "the listener watching the Bombe machine rotors click to decrypt Nazi U-boat naval codes",
                    "ENACT IN REAL-TIME: Do NOT summarize! 1941, Bletchley Park. Midnight, bitter cold. Rotors of the electromechanical Bombe click and clatter rhythmically like hundreds of ticking clocks. German U-boats are hunting Allied convoys in the Atlantic. Joan Clarke runs in: 'Alan, the weather intercept from the Baltic just came in!' Alan leans over the enigma wheels: 'The crib... C-R-I-B. If 'wetter' is at 0600 hours...' Enact the race against the clock."
                ),
                Scenario(
                    "story_einstein",
                    "Albert Einstein & Năm kỳ diệu 1905",
                    "Albert Einstein & The Miracle Year 1905",
                    "a master audio drama voice actor enacting the young Swiss patent clerk who reimagined space and time",
                    "the listener riding along a beam of light in Einstein's famous thought experiments",
                    "ENACT IN REAL-TIME: Do NOT summarize! Bern, Switzerland, spring 1905. Clock tower bells chime outside the patent office. 26-year-old Einstein pushes aside technical drawings of gravel sorters. He leans over a blank sheet of paper, eyes wide: 'Michele, imagine what happens if a man falls freely from the roof of a house... he will not feel his own weight!' Enact the passionate philosophical debate."
                )
            )
        ),
        Topic(
            id = "story_science",
            titleVi = "Khoa học & Bí ẩn vũ trụ",
            titleEn = "Science & Wonders of the Universe",
            description = "Thrilling scientific breakthroughs, cosmic mysteries, deep sea wonders, and medical discoveries.",
            emoji = "🔬",
            keywords = listOf(
                "science", "discovery", "universe", "space", "black hole", "penicillin", "deep sea",
                "ai", "artificial intelligence", "khoa hoc", "kham pha", "vu tru", "ho den", "bien sau"
            ),
            scenarios = listOf(
                Scenario(
                    "story_penicillin",
                    "Khám phá tình cờ ra Penicillin",
                    "The Serendipitous Discovery of Penicillin",
                    "a master audio drama voice actor enacting Dr. Alexander Fleming's London laboratory breakthrough",
                    "the listener witnessing how an unwashed laboratory dish saved hundreds of millions of lives",
                    "ENACT IN REAL-TIME: Do NOT summarize! September 1928, St Mary's Hospital, London. Dr. Alexander Fleming returns from Scottish holiday to his cluttered second-floor laboratory. Rain streaks the sooty windowpane. He sorts through a stack of neglected glass petri dishes of staphylococci. He stops, holds one up to the gray morning light, and mutters: 'That's funny...' A halo of clear fluid surrounding blue-green mold. Enact the moment of discovery."
                ),
                Scenario(
                    "story_black_holes",
                    "Bí ẩn hố đen & Chân trời sự kiện",
                    "The Enigma of Black Holes & Event Horizon",
                    "a master cosmic audio drama storyteller enacting the Event Horizon Telescope breakthrough",
                    "the listener journeying with the Event Horizon Telescope team capturing the first image of M87*",
                    "ENACT IN REAL-TIME: Do NOT summarize! April 2017. Telescopes synced from the South Pole to the volcanic summit of Mauna Kea. Submillimeter atomic clocks ticking in unison. Katie Bouman and astrophysicists wait as supercomputers process petabytes of hard drive data. A circular orange silhouette slowly forms on the screen: 'There it is. The shadow of infinity.' Enact the triumph."
                ),
                Scenario(
                    "story_deep_sea",
                    "Kỳ quan sinh vật rãnh Mariana sâu nhất Trái Đất",
                    "Wonders of the Mariana Trench: Challenger Deep",
                    "a master audio drama voice actor enacting the Challenger Deep descent",
                    "the listener witnessing bioluminescent creature wonders at extreme hydrothermal vents",
                    "ENACT IN REAL-TIME: Do NOT summarize! Submersible cockpit, 35,800 feet below the Pacific. The steel hull groans under one thousand atmospheres of crushing hydrostatic pressure. Exterior thermometer reads 1 degree Celsius. Don Walsh and Jacques Piccard look through the thick conical acrylic port. Outside in the absolute black abyss, a translucent flatfish drifts past the searchlight. Enact the awe and peril."
                ),
                Scenario(
                    "story_ai_evolution",
                    "Sự trỗi dậy của Trí tuệ Nhân tạo",
                    "The Dawn of Artificial Intelligence: Turing to Transformers",
                    "a master audio drama voice actor enacting pivotal milestones in AI history",
                    "the listener witnessing the milestone matches of Deep Blue, AlphaGo, and generative language models",
                    "ENACT IN REAL-TIME: Do NOT summarize! Seoul, March 2016, Game 2 of AlphaGo vs Lee Sedol. Silence in the Four Seasons Hotel room. Move 37: DeepMind's system places a black stone on the fifth line—a move no human master has played in 3,000 years. Commentator gasps: 'That's... that is not a human move!' Lee Sedol stands up from the board, eyes shaking. Enact the watershed moment."
                )
            )
        ),
        Topic(
            id = "story_history",
            titleVi = "Lịch sử & Bí ẩn thế giới",
            titleEn = "History & Unsolved Mysteries",
            description = "Enigmatic archaeological puzzles, ancient civilizations, daring voyages, and historic events.",
            emoji = "🏛️",
            keywords = listOf(
                "history", "mystery", "pyramid", "bermuda", "silk road", "titanic", "ancient", "roanoke",
                "lich su", "bi an", "kim tu thap", "tam giac bermuda", "con duong to lua", "tau titanic"
            ),
            scenarios = listOf(
                Scenario(
                    "story_titanic",
                    "Đêm định mệnh của con tàu Titanic 1912",
                    "The Untold Stories of the Titanic 1912",
                    "a master audio drama voice actor enacting the Marconi radio room and final hours of the Titanic",
                    "the listener reliving the wireless distress calls from the North Atlantic",
                    "ENACT IN REAL-TIME: Do NOT summarize! April 14, 1912, 11:40 PM. Freezing calm Atlantic. Suddenly, a violent grinding shudder shivers through the hull. In the Marconi wireless cabin, Jack Phillips is transmitting passengers' telegrams. Captain Smith pushes through the doorway, face pale as chalk: 'We've struck an iceberg. Send the call for assistance.' Spark transmitter buzzes loudly: CQD... MGY... SOS! Enact the frantic wireless calls and rising waters."
                ),
                Scenario(
                    "story_pyramids",
                    "Bí ẩn xây dựng Đại kim tự tháp Giza",
                    "The Engineering Secrets of the Great Pyramids",
                    "a master archaeological audio drama storyteller enacting the construction of Khufu's pyramid",
                    "the listener examining the stellar alignment, water-flushed ramp theories, and internal voids",
                    "ENACT IN REAL-TIME: Do NOT summarize! 2560 BC, Nile flood season. The desert heat shimmers on the Giza plateau. 20,000 stonecutters and overseers chant in unison as a 15-ton limestone block slides over wet silt rollers. Chief architect Hemiunu stands atop the rising terraced ramp, checking shadows against the constellation Orion. Enact the colossal engineering effort."
                ),
                Scenario(
                    "story_bermuda",
                    "Tam giác Bermuda: Huyền thoại và sự thật",
                    "The Bermuda Triangle: Flight 19 and Ocean Science",
                    "a master aviation audio drama voice actor enacting the disappearance of Flight 19",
                    "the listener investigating the mysterious disappearance of five Navy Avenger bombers in 1945",
                    "ENACT IN REAL-TIME: Do NOT summarize! December 5, 1945, 3:45 PM. Five TBM Avenger bombers over the Atlantic. Static cracks through the radio. Lieutenant Taylor reports in distress: 'Both of my compasses are out! I cannot find land. Everything is wrong... even the ocean doesn't look as it should.' Enact the eerie radio transmissions and stormy dusk."
                ),
                Scenario(
                    "story_silk_road",
                    "Con đường tơ lụa huyền thoại qua sa mạc Taklamakan",
                    "Caravans of the Ancient Silk Road",
                    "a master historical audio drama storyteller enacting a perilous caravan trek",
                    "the listener traveling alongside ancient merchants, translators, and astronomical navigators",
                    "ENACT IN REAL-TIME: Do NOT summarize! Sunset on the edge of the Taklamakan Desert, 'The Sea of Death'. Camel bells chime rhythmically. A sudden ominous yellow haze fills the western horizon—the deadly black hurricane sandstorm, Kara-buran. Caravan master Zhang Qian shouts: 'Camels down! Cover your faces with silk cloth!' Wind shrieks across the dunes. Enact the desert battle for survival."
                )
            )
        )
    )

    fun getAllTopics(): List<Topic> = topics

    fun getStoryTopics(): List<Topic> = topics.filter { it.id.startsWith("story_") }

    fun getConversationTopics(): List<Topic> = topics.filterNot { it.id.startsWith("story_") }

    fun isStoryTopic(topicId: String?): Boolean = topicId?.startsWith("story_") == true

    fun getTopicById(id: String?): Topic? = topics.find { it.id == id }

    fun getScenario(scenarioId: String?): Pair<Topic, Scenario>? {
        if (scenarioId == null) return null
        for (topic in topics) {
            topic.scenarios.find { it.id == scenarioId }?.let { return topic to it }
        }
        if (scenarioId.startsWith(DYNAMIC_PREFIX)) {
            return resolveDynamicScenario(scenarioId)
        }
        return null
    }

    /**
     * Resolves dynamically generated scenarios that are not hardcoded in the topic list.
     * Supports:
     * - `dynamic_story_random`: AI surprise story.
     * - `dynamic_story_custom_<slug>`: dynamic custom story requested by learner.
     * - `dynamic_story_recommended_<topicId>`: dynamic personalized story recommendation.
     * - `dynamic_explore_<topicId>`: dynamic open-ended situation generated by AI.
     * - `dynamic_custom_<topicId>_<slug>`: dynamic custom situation requested by learner.
     */
    fun resolveDynamicScenario(scenarioId: String): Pair<Topic, Scenario>? {
        if (!scenarioId.startsWith(DYNAMIC_PREFIX)) return null
        val body = scenarioId.removePrefix(DYNAMIC_PREFIX)
        return when {
            body.startsWith("story_random") -> {
                val storyTopic = getStoryTopics().randomOrNull() ?: topics.first()
                val dynamicScenario = Scenario(
                    id = scenarioId,
                    titleVi = "Chuyện Ngẫu nhiên Bất ngờ",
                    titleEn = "Surprise Story",
                    aiRole = "a captivating, world-class storyteller and listening coach",
                    learnerRole = "an engaged listener discovering a fascinating story",
                    customContext = "Tell an unexpected, thrilling and educational short story from world history, science, human triumph, or mysterious wonders. Hook the listener right away."
                )
                storyTopic to dynamicScenario
            }
            body.startsWith("story_search_") -> {
                val query = body.removePrefix("story_search_").replace('_', ' ').trim()
                val storyTopic = findTopicByQuery(query) ?: getStoryTopics().first()
                val displayContext = query.ifBlank { storyTopic.titleVi }
                val dynamicScenario = Scenario(
                    id = scenarioId,
                    titleVi = "Truyện mạng: $displayContext",
                    titleEn = "Web Story: $displayContext",
                    aiRole = "a master audio drama storyteller and voice actor enacting this authentic story with character voices",
                    learnerRole = "an engaged listener following the authentic tale",
                    customContext = "ENACT IN REAL-TIME: Do NOT summarize the plot! Drop the listener straight into the high-stakes opening scene about $displayContext with environmental atmosphere, direct character dialogue, and real-time suspense."
                )
                storyTopic to dynamicScenario
            }
            body.startsWith("story_custom_") -> {
                val rawContext = body.removePrefix("story_custom_").replace('_', ' ').trim()
                val storyTopic = findTopicByQuery(rawContext) ?: getStoryTopics().first()
                val displayContext = rawContext.ifBlank { storyTopic.titleVi }
                val dynamicScenario = Scenario(
                    id = scenarioId,
                    titleVi = "Câu chuyện: $displayContext",
                    titleEn = "Story: $displayContext",
                    aiRole = "a master audio drama storyteller and voice actor enacting this dramatic tale",
                    learnerRole = "an eager listener learning English through the story of $displayContext",
                    customContext = "ENACT IN REAL-TIME: Do NOT summarize! Drop the listener straight into the opening scene of $displayContext with atmospheric soundscapes and direct character dialogue."
                )
                storyTopic to dynamicScenario
            }
            body.startsWith("story_recommended_") -> {
                val topicId = body.removePrefix("story_recommended_")
                val topic = getTopicById(topicId) ?: getStoryTopics().first()
                val dynamicScenario = Scenario(
                    id = scenarioId,
                    titleVi = "Chuyện AI gợi ý theo sở thích",
                    titleEn = "AI Personalized Story",
                    aiRole = "a master audio drama storyteller and voice actor enacting an episodic tale tailored to the listener's favorite themes in ${topic.titleEn}",
                    learnerRole = "the listener enjoying a fresh personalized story",
                    customContext = "ENACT IN REAL-TIME: Do NOT summarize! Start immediately with Chapter One: unfold the opening scene in real-time with direct character dialogue and rich suspense."
                )
                topic to dynamicScenario
            }
            body.startsWith("explore_") -> {
                val topicId = body.removePrefix("explore_")
                val topic = getTopicById(topicId) ?: return null
                val dynamicScenario = Scenario(
                    id = scenarioId,
                    titleVi = "Khám phá tình huống AI mới",
                    titleEn = "AI Scenario Exploration",
                    aiRole = "an authentic specialist, colleague or local partner presenting a realistic challenge in ${topic.titleEn}",
                    learnerRole = "the professional, traveler or participant addressing this situation",
                    customContext = "Dynamically introduce an authentic, unexpected, and realistic situation in the domain of ${topic.titleEn} (${topic.titleVi}). Expand beyond standard textbook dialogue."
                )
                topic to dynamicScenario
            }
            body.startsWith("custom_") -> {
                val rest = body.removePrefix("custom_")
                val topicId = rest.substringBefore('_')
                val rawContext = rest.substringAfter('_', "").replace('_', ' ').trim()
                val topic = getTopicById(topicId) ?: findTopicByQuery(rawContext) ?: topics.first()
                val displayContext = rawContext.ifBlank { topic.titleVi }
                val dynamicScenario = Scenario(
                    id = scenarioId,
                    titleVi = "Tình huống tự chọn: $displayContext",
                    titleEn = "Custom Scenario: $displayContext",
                    aiRole = "a relevant specialist or counterpart discussing $displayContext in ${topic.titleEn}",
                    learnerRole = "the professional or participant handling $displayContext",
                    customContext = "The learner has chosen to practice this specific situation in ${topic.titleEn}: $displayContext. Conduct a realistic roleplay with authentic terminology and practical depth."
                )
                topic to dynamicScenario
            }
            else -> {
                val topic = getTopicById(body) ?: return null
                topic to Scenario(
                    id = scenarioId,
                    titleVi = "Khám phá chuyên sâu",
                    titleEn = "Open Exploration",
                    aiRole = "a knowledgeable conversational partner in ${topic.titleEn}",
                    learnerRole = "an active participant discussing real-world aspects of ${topic.titleEn}"
                )
            }
        }
    }

    /**
     * Creates a custom dynamic scenario for a given topic and specific context string.
     */
    fun createCustomScenario(topicId: String, context: String): Scenario {
        val safeSlug = normalize(context).replace(' ', '_').take(40)
        val scenarioId = "${DYNAMIC_PREFIX}custom_${topicId}_$safeSlug"
        return resolveDynamicScenario(scenarioId)?.second ?: Scenario(
            id = scenarioId,
            titleVi = "Tình huống: $context",
            titleEn = "Scenario: $context",
            aiRole = "a specialist in $context",
            learnerRole = "the learner handling $context",
            customContext = context
        )
    }

    /**
     * Creates a custom dynamic story scenario for listening practice.
     */
    fun createCustomStoryScenario(context: String): Scenario {
        val safeSlug = normalize(context).replace(' ', '_').take(40)
        val scenarioId = "${DYNAMIC_PREFIX}story_custom_$safeSlug"
        return resolveDynamicScenario(scenarioId)?.second ?: Scenario(
            id = scenarioId,
            titleVi = "Câu chuyện: $context",
            titleEn = "Story: $context",
            aiRole = "a master audio drama storyteller and voice actor enacting $context",
            learnerRole = "an eager listener discovering $context",
            customContext = "ENACT IN REAL-TIME: Do NOT summarize the plot! Drop the listener straight into the opening scene of $context with atmospheric sensory sounds and direct character dialogue."
        )
    }

    /**
     * Searches for a story based on learner query. Directs Gemini to adapt authentic web literature
     * or documented accounts about the query.
     */
    fun searchWebStory(query: String): Pair<Topic, Scenario> {
        val safeSlug = slugify(query).take(40)
        val scenarioId = "${DYNAMIC_PREFIX}story_search_$safeSlug"
        val matchedTopic = findTopicByQuery(query) ?: getStoryTopics().first()
        val storyTopic = if (isStoryTopic(matchedTopic.id)) matchedTopic else getStoryTopics().first()
        return storyTopic to (resolveDynamicScenario(scenarioId)?.second ?: Scenario(
            id = scenarioId,
            titleVi = "Truyện mạng: $query",
            titleEn = "Web Story: $query",
            aiRole = "a master audio drama storyteller and voice actor enacting this authentic story with character voices",
            learnerRole = "an engaged listener following the authentic tale",
            customContext = "ENACT IN REAL-TIME: Do NOT summarize the plot! This story is adapted from authentic online documents, literature, or real-world chronicles about: $query. Drop the listener straight into the high-stakes opening scene with direct character dialogue and sensory soundscapes."
        ))
    }

    /**
     * Finds the topic a spoken query refers to, e.g. "travel English", "phỏng vấn".
     * Matching ignores case and Vietnamese diacritics.
     */
    fun findTopicByQuery(query: String): Topic? {
        val q = normalize(query)
        if (q.isBlank()) return null
        // The longest matching phrase wins, so "job interview" beats the "job" keyword of Work.
        return topics
            .map { topic -> topic to matchLength(q, topic) }
            .filter { (_, length) -> length > 0 }
            .maxByOrNull { (_, length) -> length }
            ?.first
    }

    private fun matchLength(q: String, topic: Topic): Int =
        (listOf(topic.id, topic.titleEn, topic.titleVi) + topic.keywords).maxOf { candidate ->
            val c = normalize(candidate)
            // Whole-word match either way so "work" does not match "network".
            when {
                c == q -> c.length * 2
                containsWord(q, c) -> c.length
                containsWord(c, q) -> q.length
                else -> 0
            }
        }

    /** Prefers topics the learner has not practised recently. */
    fun suggestTopic(recentTopicIds: List<String>): Topic {
        val unvisited = topics.filterNot { it.id in recentTopicIds }
        return (unvisited.ifEmpty { topics }).random()
    }

    companion object {
        const val DYNAMIC_PREFIX = "dynamic_"

        /** Lower-cases and strips Vietnamese diacritics: "Phỏng vấn" -> "phong van". */
        fun normalize(text: String): String {
            val decomposed = Normalizer.normalize(text.lowercase(), Normalizer.Form.NFD)
            return decomposed
                .replace(Regex("\\p{Mn}+"), "")
                .replace('đ', 'd')
                .replace(Regex("[^a-z0-9 ]"), " ")
                .replace(Regex("\\s+"), " ")
                .trim()
        }

        /** Converts free-form text to a safe identifier slug: "Elon Musk" -> "elon_musk". */
        fun slugify(text: String): String =
            normalize(text).replace(" ", "_")

        private fun containsWord(haystack: String, needle: String): Boolean {
            if (needle.isBlank()) return false
            return Regex("(^| )${Regex.escape(needle)}( |$)").containsMatchIn(haystack)
        }
    }
}
