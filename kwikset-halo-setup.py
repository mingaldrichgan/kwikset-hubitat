import asyncio

from aiokwikset import API


async def main() -> None:
    api = API(input("Email: "))
    pre_auth = await api.authenticate(input("Password: "))
    await api.verify_user(pre_auth, input("Verification Code: "))
    homes = await api.user.get_homes()

    print()
    match len(homes):
        case 0:
            print("You have no homes! Please download the Kwikset app and add one.")
            return
        case 1:
            print("Home ID:", homes[0]['homeid'])
        case _:
            print(f"You have {len(homes)} homes!")
            print()
            for home in homes:
                print("Home ID:", home['homeid'])
                print("Home Name:", home['homename'])
                print("Owner Name:", home['ownername'])
                print("Owner Email:", home['email'])

    print()
    print("Refresh Token:", api.refresh_token)


asyncio.run(main())
